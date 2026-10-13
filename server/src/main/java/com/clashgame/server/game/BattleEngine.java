package com.clashgame.server.game;

import com.clashgame.server.model.*;
import com.clashgame.server.network.GameServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Core battle simulation engine.
 * Runs a real-time battle between an attacker's army and a defender's base.
 * 
 * The battle simulation runs on a dedicated thread with a fixed time step.
 * 
 * Battle Flow:
 * 1. Attacker deploys units at edge of defender's base grid
 * 2. Units move toward nearest building and attack it
 * 3. Defensive buildings target and attack nearby enemy units
 * 4. Battle ends when all units die, all buildings destroyed, or time expires
 * 5. Stars and loot are calculated based on destruction percentage
 * 
 * Thread Safety:
 * - Unit and building lists use CopyOnWriteArrayList for concurrent iteration
 * - Battle state updates are synchronized
 */
public class BattleEngine implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(BattleEngine.class);

    // Battle configuration
    private static final double TICK_RATE = 20.0;               // ticks per second
    private static final double TICK_INTERVAL = 1.0 / TICK_RATE; // seconds per tick
    private static final int MAX_BATTLE_DURATION = 180;          // 3 minutes
    private static final double DEFENSE_RANGE = 5.0;             // cells

    // Battle identity
    private final String battleId;
    private final Battle battle;
    private final GameServer gameServer;

    // Combatants
    private final Player attacker;
    private final Player defender;
    private final Base defenderBase;

    // Active entities in the battle
    private final CopyOnWriteArrayList<Unit> activeUnits = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Building> activeBuildings = new CopyOnWriteArrayList<>();
    private final List<Building> defensiveBuildings = new ArrayList<>();

    // Battle state
    private volatile boolean running;
    private double elapsedTime;         // seconds
    private double lastUpdateSentTime;  // for throttling updates
    private static final double UPDATE_INTERVAL = 0.25; // send updates every 0.25s (4/sec)
    private int initialBuildingCount;
    private int destroyedBuildingCount;
    private int deployedUnitCount;
    private int lostUnitCount;

    // Defense fire events accumulated between updates (cleared after each sendBattleUpdate)
    private final List<DefenseFireEvent> pendingFireEvents = new ArrayList<>();

    // Callback for when battle completes
    private BattleCompleteCallback onComplete;

    // ─── Constructor ─────────────────────────────────────────────────

    public BattleEngine(String battleId, Battle battle, Player attacker,
                        Player defender, GameServer gameServer) {
        this.battleId = battleId;
        this.battle = battle;
        this.attacker = attacker;
        this.defender = defender;
        this.defenderBase = defender.getBase();
        this.gameServer = gameServer;
        this.running = false;
        this.elapsedTime = 0;
        this.destroyedBuildingCount = 0;
        this.deployedUnitCount = 0;
        this.lostUnitCount = 0;
        this.lastUpdateSentTime = 0;

        initializeBattle();
    }

    /**
     * Set up the battle field with defender's buildings.
     */
    private void initializeBattle() {
        // Copy defender's buildings into active battle list
        for (Building building : defenderBase.getBuildings()) {
            building.repair(); // Ensure full HP
            activeBuildings.add(building);
            // Only actual defense buildings fire at units (NOT walls)
            if (building.getDefenseValue() > 0 && building.getType() != BuildingType.WALL) {
                defensiveBuildings.add(building);
            }
        }
        this.initialBuildingCount = activeBuildings.size();

        logger.info("Battle {} initialized: {} vs {}, {} buildings",
                battleId, attacker.getUsername(), defender.getUsername(), initialBuildingCount);
    }

    // ─── Main Battle Loop ────────────────────────────────────────────

    @Override
    public void run() {
        running = true;
        battle.start();
        logger.info("Battle {} started!", battleId);

        long lastTick = System.nanoTime();

        while (running) {
            long now = System.nanoTime();
            double deltaTime = (now - lastTick) / 1_000_000_000.0;

            if (deltaTime >= TICK_INTERVAL) {
                lastTick = now;
                elapsedTime += deltaTime;

                // Execute one simulation tick
                tick(deltaTime);

                // Send battle state update (throttled to 4/sec)
                if (elapsedTime - lastUpdateSentTime >= UPDATE_INTERVAL) {
                    sendBattleUpdate();
                    lastUpdateSentTime = elapsedTime;
                }

                // Check end conditions
                if (shouldBattleEnd()) {
                    endBattle();
                    return;
                }
            }

            // Prevent busy-waiting
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                endBattle();
                return;
            }
        }
    }

    /**
     * Execute one simulation tick.
     * 1. Move units toward targets
     * 2. Units attack buildings (or heal allies for Healers)
     * 3. Defensive buildings attack units
     * 4. Remove dead entities
     */
    private synchronized void tick(double deltaTime) {
        // Phase 1: Unit AI - Move and attack
        for (Unit unit : activeUnits) {
            if (!unit.isAlive()) continue;

            // Healer AI: heal nearby friendly units instead of attacking buildings
            if (unit.getType() == UnitType.HEALER) {
                Unit healTarget = findMostDamagedUnit(unit);
                if (healTarget != null) {
                    double dist = calculateDistance(unit.getBattleX(), unit.getBattleY(),
                            healTarget.getBattleX(), healTarget.getBattleY());
                    if (dist > 2.0) {
                        unit.moveToward(healTarget.getBattleX(), healTarget.getBattleY(), deltaTime);
                    } else {
                        unit.incrementAttackTimer(deltaTime);
                        if (unit.getAttackTimer() >= 1.0) {
                            unit.resetAttackTimer();
                            int healAmount = 20 + unit.getLevel() * 5;
                            healTarget.heal(healAmount);
                        }
                    }
                }
                continue; // Healers don't attack buildings
            }

            // Find target if none or target destroyed
            if (unit.getTargetBuilding() == null || unit.getTargetBuilding().isDestroyed()) {
                Building nearestBuilding = findNearestBuilding(unit);
                unit.setTargetBuilding(nearestBuilding);
            }

            Building target = unit.getTargetBuilding();
            if (target == null) continue; // no buildings left

            // Move toward target
            double targetX = target.getGridX() + target.getWidth() / 2.0;
            double targetY = target.getGridY() + target.getHeight() / 2.0;
            double distance = calculateDistance(unit.getBattleX(), unit.getBattleY(), targetX, targetY);

            if (distance > 1.5) {
                // Move toward target
                unit.moveToward(targetX, targetY, deltaTime);
            } else {
                // In range - attack with cooldown (once per 0.5 seconds)
                unit.incrementAttackTimer(deltaTime);
                if (unit.getAttackTimer() >= 0.5) {
                    unit.resetAttackTimer();
                    boolean destroyed = unit.attackBuilding(target);
                    if (destroyed) {
                        destroyedBuildingCount++;
                        logger.debug("Building {} destroyed by {}", target.getType(), unit.getType());
                    }
                }
            }
        }

        // Phase 2: Defense AI - Defensive buildings attack nearby units (with cooldown)
        for (Building defense : defensiveBuildings) {
            if (defense.isDestroyed()) continue;

            // Fire once per second instead of every tick
            defense.incrementAttackTimer(deltaTime);
            if (defense.getAttackTimer() < 1.0) continue;
            defense.resetAttackTimer();

            Unit nearestUnit = findNearestUnit(defense);
            if (nearestUnit == null) continue;

            double distance = calculateDistance(
                    defense.getGridX(), defense.getGridY(),
                    nearestUnit.getBattleX(), nearestUnit.getBattleY());

            if (distance <= DEFENSE_RANGE) {
                int damage = defense.getDefenseValue();

                // Record fire event for client animation
                pendingFireEvents.add(new DefenseFireEvent(
                        defense.getType().name(),
                        defense.getGridX() + defense.getWidth() / 2.0,
                        defense.getGridY() + defense.getHeight() / 2.0,
                        nearestUnit.getBattleX(),
                        nearestUnit.getBattleY(),
                        damage
                ));

                boolean killed = nearestUnit.takeDamage(damage);
                if (killed) {
                    lostUnitCount++;
                    logger.debug("Unit {} killed by {}", nearestUnit.getType(), defense.getType());
                }
            }
        }

        // Phase 3: Cleanup - Remove dead entities
        activeUnits.removeIf(u -> !u.isAlive());
        activeBuildings.removeIf(Building::isDestroyed);
    }

    // ─── Unit Deployment ─────────────────────────────────────────────

    /**
     * Deploy a unit at the specified position during battle.
     * Called when the attacker places a unit on the battle field.
     * @param unit the unit to deploy
     * @param x initial x position on the grid edge
     * @param y initial y position on the grid edge
     */
    public synchronized void deployUnit(Unit unit, double x, double y) {
        if (!running) return;
        unit.setBattleX(x);
        unit.setBattleY(y);
        unit.setAlive(true);
        activeUnits.add(unit);
        deployedUnitCount++;

        logger.info("Unit {} deployed at ({}, {}) in battle {}", unit.getType(), x, y, battleId);
    }

    // ─── Target Finding ──────────────────────────────────────────────

    /**
     * Find the nearest non-destroyed building to a unit.
     * Goblin prioritizes resource buildings, Giant prioritizes defense buildings.
     */
    private Building findNearestBuilding(Unit unit) {
        Building nearest = null;
        double minDist = Double.MAX_VALUE;

        // Priority targeting for specific unit types
        boolean preferResources = unit.getType() == UnitType.GOBLIN;
        boolean preferDefenses = unit.getType() == UnitType.GIANT;

        // First pass: look for preferred targets
        if (preferResources || preferDefenses) {
            for (Building building : activeBuildings) {
                if (building.isDestroyed()) continue;
                boolean match = false;
                if (preferResources) {
                    BuildingType bt = building.getType();
                    match = bt == BuildingType.GOLD_MINE || bt == BuildingType.ELIXIR_COLLECTOR
                         || bt == BuildingType.GOLD_STORAGE || bt == BuildingType.ELIXIR_STORAGE;
                } else { // preferDefenses
                    BuildingType bt = building.getType();
                    match = bt == BuildingType.CANNON || bt == BuildingType.ARCHER_TOWER
                         || bt == BuildingType.MORTAR;
                }
                if (!match) continue;
                double dist = calculateDistance(
                        unit.getBattleX(), unit.getBattleY(),
                        building.getGridX() + building.getWidth() / 2.0,
                        building.getGridY() + building.getHeight() / 2.0);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = building;
                }
            }
            if (nearest != null) return nearest;
        }

        // Fallback: nearest building of any type
        minDist = Double.MAX_VALUE;
        for (Building building : activeBuildings) {
            if (building.isDestroyed()) continue;
            double dist = calculateDistance(
                    unit.getBattleX(), unit.getBattleY(),
                    building.getGridX() + building.getWidth() / 2.0,
                    building.getGridY() + building.getHeight() / 2.0);
            if (dist < minDist) {
                minDist = dist;
                nearest = building;
            }
        }
        return nearest;
    }

    /**
     * Find the most damaged friendly unit near a healer.
     */
    private Unit findMostDamagedUnit(Unit healer) {
        Unit best = null;
        int maxDamage = 0;
        for (Unit unit : activeUnits) {
            if (!unit.isAlive() || unit == healer || unit.getType() == UnitType.HEALER) continue;
            int damage = unit.getHitPoints() - unit.getCurrentHitPoints();
            if (damage > maxDamage) {
                maxDamage = damage;
                best = unit;
            }
        }
        return best;
    }

    /**
     * Find the nearest alive unit to a defensive building.
     */
    private Unit findNearestUnit(Building defense) {
        Unit nearest = null;
        double minDist = Double.MAX_VALUE;

        for (Unit unit : activeUnits) {
            if (!unit.isAlive()) continue;
            double dist = calculateDistance(
                    defense.getGridX(), defense.getGridY(),
                    unit.getBattleX(), unit.getBattleY());
            if (dist < minDist) {
                minDist = dist;
                nearest = unit;
            }
        }
        return nearest;
    }

    /**
     * Euclidean distance between two points.
     */
    private double calculateDistance(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // ─── Battle End Conditions ───────────────────────────────────────

    /**
     * Check if the battle should end.
     */
    private boolean shouldBattleEnd() {
        // Time limit reached
        if (elapsedTime >= MAX_BATTLE_DURATION) {
            logger.info("Battle {} ended: time expired", battleId);
            return true;
        }

        // All buildings destroyed (100% destruction)
        // Guard: only trigger if there WERE buildings initially (empty base ≠ 100% destruction)
        if (initialBuildingCount > 0 && activeBuildings.isEmpty()) {
            logger.info("Battle {} ended: all buildings destroyed", battleId);
            return true;
        }

        // All units dead and no more to deploy
        if (activeUnits.isEmpty() && deployedUnitCount > 0) {
            logger.info("Battle {} ended: all units eliminated", battleId);
            return true;
        }

        // NOTE: No early exit for "no units deployed" — attacker has the full
        // 3-minute duration to deploy units at their own pace (like Clash of Clans).

        return false;
    }

    /**
     * End the battle and calculate results.
     */
    private void endBattle() {
        running = false;

        double destructionPct = (initialBuildingCount > 0)
                ? (destroyedBuildingCount * 100.0 / initialBuildingCount)
                : 0.0;

        battle.setAttackerUnitsDeployed(deployedUnitCount);
        battle.setAttackerUnitsLost(lostUnitCount);
        battle.setBattleDuration((int) elapsedTime);
        battle.complete(destructionPct, destroyedBuildingCount, initialBuildingCount);

        // Apply battle rewards
        applyBattleRewards();

        // Send final result to both players
        String resultJson = buildBattleResultJson();
        gameServer.sendToPlayer(attacker.getId().toString(),
                GameMessage.battleResult(resultJson));
        gameServer.sendToPlayer(defender.getId().toString(),
                GameMessage.battleResult(resultJson));

        logger.info("Battle {} complete: {}% destruction, {} stars",
                battleId, String.format("%.1f", destructionPct), battle.getStarsEarned());

        // Invoke completion callback
        if (onComplete != null) {
            onComplete.onBattleComplete(battle);
        }
    }

    /**
     * Apply loot and trophy rewards/penalties to players.
     * NOTE: Actual persistence is done in BattleService.saveBattleResult()
     * which reloads fresh entities. These in-memory updates are for the
     * immediate JSON response to clients only.
     */
    private void applyBattleRewards() {
        // In-memory updates for immediate BATTLE_RESULT message
        // BattleService.saveBattleResult() will reload fresh entities and persist
    }

    /**
     * Build JSON string with battle results.
     */
    private String buildBattleResultJson() {
        return String.format(
                "{\"battleId\":\"%s\",\"attacker\":\"%s\",\"defender\":\"%s\"," +
                "\"stars\":%d,\"destruction\":%.1f,\"goldLooted\":%d," +
                "\"elixirLooted\":%d,\"trophiesWon\":%d,\"trophiesLost\":%d," +
                "\"unitsDeployed\":%d,\"unitsLost\":%d,\"duration\":%d}",
                battleId, attacker.getUsername(), defender.getUsername(),
                battle.getStarsEarned(), battle.getDestructionPercentage(),
                battle.getGoldLooted(), battle.getElixirLooted(),
                battle.getTrophiesWon(), battle.getTrophiesLost(),
                deployedUnitCount, lostUnitCount, (int) elapsedTime
        );
    }

    /**
     * Send periodic battle state update to both players.
     * Includes full unit positions and building health for client-side rendering.
     */
    private void sendBattleUpdate() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "{\"battleId\":\"%s\",\"elapsed\":%.1f,\"unitsAlive\":%d," +
                "\"buildingsRemaining\":%d,\"destruction\":%.1f,",
                battleId, elapsedTime, activeUnits.size(),
                activeBuildings.size(),
                (initialBuildingCount > 0 ? destroyedBuildingCount * 100.0 / initialBuildingCount : 0)
        ));

        // Include live unit positions for animation
        sb.append("\"units\":[");
        int ui = 0;
        for (Unit unit : activeUnits) {
            if (!unit.isAlive()) continue;
            if (ui > 0) sb.append(",");
            sb.append(String.format(
                "{\"type\":\"%s\",\"x\":%.2f,\"y\":%.2f,\"hp\":%d,\"maxHp\":%d}",
                unit.getType(), unit.getBattleX(), unit.getBattleY(),
                unit.getCurrentHitPoints(), unit.getHitPoints()));
            ui++;
        }
        sb.append("],");

        // Include live building health for damage visualization
        sb.append("\"buildings\":[");
        List<Building> allOriginal = defenderBase.getBuildings();
        for (int i = 0; i < allOriginal.size(); i++) {
            Building b = allOriginal.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format(
                "{\"type\":\"%s\",\"gridX\":%d,\"gridY\":%d,\"width\":%d,\"height\":%d," +
                "\"hp\":%d,\"maxHp\":%d,\"destroyed\":%b}",
                b.getType(), b.getGridX(), b.getGridY(), b.getWidth(), b.getHeight(),
                b.getCurrentHitPoints(), b.getHitPoints(), b.isDestroyed()));
        }
        sb.append("]}");

        // Include defense fire events for projectile animation
        // These accumulate between updates and are cleared after sending
        sb.deleteCharAt(sb.length() - 1); // remove trailing }
        sb.append(",\"fireEvents\":[");
        for (int fi = 0; fi < pendingFireEvents.size(); fi++) {
            DefenseFireEvent fe = pendingFireEvents.get(fi);
            if (fi > 0) sb.append(",");
            sb.append(String.format(
                "{\"defType\":\"%s\",\"fromX\":%.2f,\"fromY\":%.2f,\"toX\":%.2f,\"toY\":%.2f,\"damage\":%d}",
                fe.defenseType, fe.fromX, fe.fromY, fe.toX, fe.toY, fe.damage));
        }
        sb.append("]}");
        pendingFireEvents.clear();

        GameMessage update = GameMessage.battleUpdate(sb.toString());
        gameServer.sendToPlayer(attacker.getId().toString(), update);
        gameServer.sendToPlayer(defender.getId().toString(), update);
    }

    // ─── Force Stop ──────────────────────────────────────────────────

    /**
     * Force-stop the battle (e.g., if a player disconnects).
     */
    public void forceStop() {
        running = false;
    }

    /**
     * Build JSON representing the defender's base layout for the client.
     */
    public String buildDefenderBaseJson() {
        StringBuilder sb = new StringBuilder("{\"battleId\":\"" + battleId + "\",\"defender\":\"" + defender.getUsername() + "\",\"buildings\":[");
        List<Building> buildings = defenderBase.getBuildings();
        for (int i = 0; i < buildings.size(); i++) {
            Building b = buildings.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format(
                "{\"type\":\"%s\",\"level\":%d,\"gridX\":%d,\"gridY\":%d,\"width\":%d,\"height\":%d,\"hitPoints\":%d,\"defenseValue\":%d}",
                b.getType(), b.getLevel(), b.getGridX(), b.getGridY(),
                b.getWidth(), b.getHeight(), b.getHitPoints(), b.getDefenseValue()));
        }
        sb.append("]}");
        return sb.toString();
    }

    // ─── Getters ─────────────────────────────────────────────────────

    public String getBattleId() { return battleId; }
    public Battle getBattle() { return battle; }
    public boolean isRunning() { return running; }
    public double getElapsedTime() { return elapsedTime; }
    public int getDestroyedBuildingCount() { return destroyedBuildingCount; }
    public Player getAttacker() { return attacker; }
    public Player getDefender() { return defender; }

    public void setOnComplete(BattleCompleteCallback onComplete) {
        this.onComplete = onComplete;
    }

    /**
     * Callback interface for battle completion.
     */
    @FunctionalInterface
    public interface BattleCompleteCallback {
        void onBattleComplete(Battle battle);
    }

    /**
     * Record of a single defense fire event for client-side animation.
     */
    private static class DefenseFireEvent {
        final String defenseType;
        final double fromX, fromY;  // defense center position
        final double toX, toY;      // target unit position
        final int damage;

        DefenseFireEvent(String defenseType, double fromX, double fromY,
                         double toX, double toY, int damage) {
            this.defenseType = defenseType;
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
            this.damage = damage;
        }
    }
}
