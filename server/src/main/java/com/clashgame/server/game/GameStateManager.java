package com.clashgame.server.game;

import com.clashgame.server.model.Battle;
import com.clashgame.server.model.GameMessage;
import com.clashgame.server.model.Player;
import com.clashgame.server.network.GameServer;
import com.clashgame.server.service.BattleService;
import com.clashgame.server.service.PlayerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Manages the overall game state, including active battles, game ticks,
 * and server-side game logic coordination.
 * 
 * Responsibilities:
 * - Track and manage active battles
 * - Manage battle simulation threads
 * - Handle game loop ticks (upgrade timers, events)
 * - Coordinate between services
 * 
 * Uses an ExecutorService for running battle simulations concurrently.
 */
@Component
public class GameStateManager {

    private static final Logger logger = LoggerFactory.getLogger(GameStateManager.class);

    // Active battles: battleId -> BattleEngine
    private final Map<String, BattleEngine> activeBattles = new ConcurrentHashMap<>();

    // Thread pool for running battle simulations
    private final ExecutorService battleExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setName("BattleSimulation-" + t.getId());
        t.setDaemon(true);
        return t;
    });

    // Scheduled executor for periodic game state updates (upgrade timers, etc.)
    private final ScheduledExecutorService gameTickExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "GameTick");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    @Lazy
    private GameServer gameServer;

    @Autowired
    @Lazy
    private BattleService battleService;

    @Autowired
    @Lazy
    private PlayerService playerService;

    // ─── Initialization ──────────────────────────────────────────────

    /**
     * Start the game tick loop for processing upgrade timers and events.
     */
    @PostConstruct
    public void startGameLoop() {
        gameTickExecutor.scheduleAtFixedRate(
                this::gameTick, 1, 1, TimeUnit.SECONDS);
        logger.info("Game loop started (1 tick/second)");
    }

    /**
     * Process one game tick: update upgrade timers, training queues, etc.
     * Runs every 1 second. Calls transactional service methods.
     */
    private void gameTick() {
        try {
            // ─── Process Building Upgrade Timers ─────────────────────
            List<long[]> completedUpgrades = playerService.processUpgradeTicks();
            
            // Notify players whose buildings finished upgrading
            for (long[] upgrade : completedUpgrades) {
                String playerId = String.valueOf(upgrade[0]);
                long buildingId = upgrade[1];
                int newLevel = (int) upgrade[2];
                
                // Notify all registered listeners (Observer Pattern)
                for (GameEventListener listener : eventListeners) {
                    try {
                        listener.onBuildingUpgradeComplete(playerId, buildingId, newLevel);
                    } catch (Exception e) {
                        logger.error("Error in GameEventListener", e);
                    }
                }
                
                // Send updated player data to the client
                String playerData = playerService.getPlayerDataJson(playerId);
                if (playerData != null && gameServer.isPlayerOnline(playerId)) {
                    gameServer.sendToPlayer(playerId, new GameMessage(
                            GameMessage.MessageType.UPDATE_BASE, "SERVER", playerData));
                }
            }
            
            // ─── Process Training Queue Timers ───────────────────────
            List<String> completedTraining = processTrainingQueue();
            
            // Send updated data for players whose training completed
            for (String playerId : completedTraining) {
                String playerData = playerService.getPlayerDataJson(playerId);
                if (playerData != null && gameServer.isPlayerOnline(playerId)) {
                    gameServer.sendToPlayer(playerId, new GameMessage(
                            GameMessage.MessageType.UPDATE_BASE, "SERVER", playerData));
                }
            }

            // ─── Process Laboratory Upgrade Timers ───────────────────
            List<Long> completedLabUpgrades = playerService.processLabUpgradeTicks();

            // Send updated data for players whose lab troop upgrade finished
            for (Long pid : completedLabUpgrades) {
                String playerId = String.valueOf(pid);
                String playerData = playerService.getPlayerDataJson(playerId);
                if (playerData != null && gameServer.isPlayerOnline(playerId)) {
                    gameServer.sendToPlayer(playerId, new GameMessage(
                            GameMessage.MessageType.UPDATE_BASE, "SERVER", playerData));
                }
            }
            
        } catch (Exception e) {
            logger.error("Error in game tick", e);
        }
    }

    // ─── Observer Pattern: Event Listeners ───────────────────────────

    private final List<GameEventListener> eventListeners = new CopyOnWriteArrayList<>();

    /**
     * Register a new game event listener (Observer Pattern).
     * @param listener the listener to register
     */
    public void addGameEventListener(GameEventListener listener) {
        eventListeners.add(listener);
    }

    /**
     * Remove a game event listener.
     * @param listener the listener to remove
     */
    public void removeGameEventListener(GameEventListener listener) {
        eventListeners.remove(listener);
    }

    // ─── Training Queue System ───────────────────────────────────────

    /**
     * Represents a unit currently being trained in the queue.
     */
    public static class TrainingItem {
        public final String playerId;
        public final String unitType;
        public int remainingSeconds;
        public final int totalCount;
        public int completedCount;

        public TrainingItem(String playerId, String unitType, int trainingTimeSeconds, int count) {
            this.playerId = playerId;
            this.unitType = unitType;
            this.remainingSeconds = trainingTimeSeconds;
            this.totalCount = count;
            this.completedCount = 0;
        }
    }

    // Training queue per player: playerId -> list of training items
    private final Map<String, List<TrainingItem>> trainingQueues = new ConcurrentHashMap<>();

    /**
     * Add units to a player's training queue.
     * Called by PlayerService.trainUnit() instead of creating units instantly.
     */
    public void addToTrainingQueue(String playerId, String unitType, int trainingTimeSeconds, int count) {
        trainingQueues.computeIfAbsent(playerId, k -> new CopyOnWriteArrayList<>())
                .add(new TrainingItem(playerId, unitType, trainingTimeSeconds, count));
        logger.info("Queued {}x {} for player {} ({}s each)", count, unitType, playerId, trainingTimeSeconds);
    }

    /**
     * Process one tick of the training queue for all players.
     * @return list of playerIds that had training complete
     */
    private List<String> processTrainingQueue() {
        List<String> completedPlayers = new ArrayList<>();
        
        for (Map.Entry<String, List<TrainingItem>> entry : trainingQueues.entrySet()) {
            String playerId = entry.getKey();
            List<TrainingItem> queue = entry.getValue();
            
            if (queue.isEmpty()) continue;
            
            // Process only the first item in the queue (one at a time)
            TrainingItem current = queue.get(0);
            current.remainingSeconds--;
            
            if (current.remainingSeconds <= 0) {
                // This unit is trained! Create it
                try {
                    playerService.createTrainedUnit(playerId, current.unitType);
                    current.completedCount++;
                    
                    logger.debug("Trained 1x {} for player {} ({}/{})",
                            current.unitType, playerId, current.completedCount, current.totalCount);
                    
                    if (current.completedCount >= current.totalCount) {
                        // All units of this batch are done
                        queue.remove(0);
                        
                        // Notify listeners
                        for (GameEventListener listener : eventListeners) {
                            try {
                                listener.onUnitTrainingComplete(playerId, current.unitType, current.totalCount);
                            } catch (Exception e) {
                                logger.error("Error in GameEventListener", e);
                            }
                        }
                    } else {
                        // Reset timer for next unit in the same batch
                        current.remainingSeconds = com.clashgame.server.model.UnitType
                                .valueOf(current.unitType).getTrainingTimeSeconds();
                    }
                    
                    completedPlayers.add(playerId);
                } catch (Exception e) {
                    logger.error("Error creating trained unit for player {}", playerId, e);
                    queue.remove(0); // Remove failed item
                }
            }
        }
        
        return completedPlayers;
    }

    /**
     * Get the training queue for a specific player (for sending to client).
     * @param playerId the player's ID
     * @return JSON string of the training queue
     */
    public String getTrainingQueueJson(String playerId) {
        List<TrainingItem> queue = trainingQueues.get(playerId);
        if (queue == null || queue.isEmpty()) return "[]";
        
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < queue.size(); i++) {
            TrainingItem item = queue.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"unitType\":\"%s\",\"remaining\":%d,\"total\":%d,\"completed\":%d}",
                    item.unitType, item.remainingSeconds, item.totalCount, item.completedCount));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Speed up training by spending gems (skips current training item).
     */
    public int speedUpTraining(String playerId) {
        List<TrainingItem> queue = trainingQueues.get(playerId);
        if (queue == null || queue.isEmpty()) return 0;
        
        TrainingItem current = queue.get(0);
        int totalRemaining = current.remainingSeconds + 
                (current.totalCount - current.completedCount - 1) * 
                com.clashgame.server.model.UnitType.valueOf(current.unitType).getTrainingTimeSeconds();
        
        // Create all remaining units instantly
        int created = 0;
        for (int i = current.completedCount; i < current.totalCount; i++) {
            try {
                playerService.createTrainedUnit(playerId, current.unitType);
                created++;
            } catch (Exception e) {
                logger.error("Error creating unit during speed-up", e);
            }
        }
        queue.remove(0);
        
        return Math.max(1, (totalRemaining + 9) / 10); // 1 gem per 10 seconds
    }

    // ─── Battle Management ───────────────────────────────────────────

    /**
     * Create and start a new battle between two players.
     * @param attacker the attacking player
     * @param defender the defending player
     * @param battle the Battle entity
     * @return the unique battle ID
     */
    public String startBattle(Player attacker, Player defender, Battle battle) {
        String battleId = UUID.randomUUID().toString();

        BattleEngine engine = new BattleEngine(battleId, battle, attacker, defender, gameServer);
        engine.setOnComplete(this::onBattleComplete);

        activeBattles.put(battleId, engine);
        battleExecutor.submit(engine);

        logger.info("Battle started: {} ({} vs {})", battleId,
                attacker.getUsername(), defender.getUsername());
        return battleId;
    }

    /**
     * Get the BattleEngine for a given battle ID.
     */
    public BattleEngine getBattleEngine(String battleId) {
        return activeBattles.get(battleId);
    }

    /**
     * Deploy a unit into an active battle.
     * @param battleId the battle to deploy into
     * @param unit the unit to deploy
     * @param x deploy x position
     * @param y deploy y position
     */
    public void deployUnitInBattle(String battleId, com.clashgame.server.model.Unit unit,
                                    double x, double y) {
        BattleEngine engine = activeBattles.get(battleId);
        if (engine != null && engine.isRunning()) {
            engine.deployUnit(unit, x, y);
        }
    }

    /**
     * Callback when a battle completes.
     */
    private void onBattleComplete(Battle battle) {
        // Remove from active battles
        activeBattles.values().removeIf(e -> e.getBattle().equals(battle));
        // Persist battle results and player stats
        try {
            battleService.saveBattleResult(battle);
        } catch (Exception e) {
            logger.error("Error saving battle result", e);
        }

        // Send refreshed player data to both attacker and defender
        try {
            String attackerId = battle.getAttacker().getId().toString();
            String defenderId = battle.getDefender().getId().toString();
            String attackerData = playerService.getPlayerDataJson(attackerId);
            String defenderData = playerService.getPlayerDataJson(defenderId);
            if (attackerData != null) {
                gameServer.sendToPlayer(attackerId, new GameMessage(
                    GameMessage.MessageType.UPDATE_BASE, "SERVER", attackerData));
            }
            if (defenderData != null) {
                gameServer.sendToPlayer(defenderId, new GameMessage(
                    GameMessage.MessageType.UPDATE_BASE, "SERVER", defenderData));
            }
        } catch (Exception e) {
            logger.error("Error sending post-battle player data", e);
        }

        logger.info("Battle completed, results saved, removed from active battles");
    }

    /**
     * Force-stop a battle (e.g., player disconnect).
     */
    public void cancelBattle(String battleId) {
        BattleEngine engine = activeBattles.remove(battleId);
        if (engine != null) {
            engine.forceStop();
            logger.info("Battle {} cancelled", battleId);
        }
    }

    // ─── Queries ─────────────────────────────────────────────────────

    /**
     * Get the number of active battles.
     */
    public int getActiveBattleCount() {
        return activeBattles.size();
    }

    /**
     * Check if a player is currently in a battle.
     */
    public boolean isPlayerInBattle(String playerId) {
        return activeBattles.values().stream()
                .anyMatch(e -> e.getBattle().getAttacker().getId().toString().equals(playerId) ||
                               e.getBattle().getDefender().getId().toString().equals(playerId));
    }

    /**
     * Shut down battle executors on application stop.
     */
    @PreDestroy
    public void shutdown() {
        activeBattles.values().forEach(BattleEngine::forceStop);
        activeBattles.clear();
        battleExecutor.shutdown();
        gameTickExecutor.shutdown();
        logger.info("GameStateManager shut down");
    }
}
