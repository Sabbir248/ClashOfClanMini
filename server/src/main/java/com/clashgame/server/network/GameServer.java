package com.clashgame.server.network;

import com.clashgame.server.model.GameMessage;
import com.clashgame.server.service.PlayerService;
import com.clashgame.server.service.BattleService;
import com.clashgame.server.service.MatchmakingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.springframework.context.annotation.Lazy;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Main game server that manages socket connections with game clients.
 * Runs on a dedicated thread, accepting client connections and spawning
 * ClientHandler threads for each connected player.
 * 
 * Architecture:
 * - ServerSocket listens for incoming connections
 * - Thread pool manages client handler threads
 * - ConcurrentHashMap stores active client sessions
 * - Broadcasts game state updates to relevant clients
 * 
 * Thread Safety:
 * - Uses ConcurrentHashMap for thread-safe client management
 * - ExecutorService for bounded thread pool
 * - All shared state access is synchronized
 */
@Component
public class GameServer {

    private static final Logger logger = LoggerFactory.getLogger(GameServer.class);

    @Value("${game.server.port:9090}")
    private int port;

    @Value("${game.server.max-clients:100}")
    private int maxClients;

    // Active client connections: playerId -> ClientHandler
    private final Map<String, ClientHandler> connectedClients = new ConcurrentHashMap<>();

    // Thread pool for client handler threads
    private ExecutorService clientThreadPool;

    // Server socket for accepting connections
    private ServerSocket serverSocket;

    // Server running flag (volatile for thread visibility)
    private volatile boolean running;

    // Server listener thread
    private Thread listenerThread;

    @Autowired
    private PlayerService playerService;

    @Autowired
    @Lazy
    private BattleService battleService;

    @Autowired
    private MatchmakingService matchmakingService;

    // ─── Lifecycle ───────────────────────────────────────────────────

    /**
     * Initialize and start the game server after Spring context is ready.
     */
    @PostConstruct
    public void start() {
        clientThreadPool = Executors.newFixedThreadPool(maxClients);
        running = true;

        listenerThread = new Thread(this::acceptConnections, "GameServer-Listener");
        listenerThread.setDaemon(true);
        listenerThread.start();

        logger.info("Game server started on port {}", port);
    }

    /**
     * Gracefully shut down the server.
     */
    @PreDestroy
    public void stop() {
        running = false;
        logger.info("Shutting down game server...");

        // Disconnect all clients
        new ArrayList<>(connectedClients.values()).forEach(ClientHandler::disconnect);
        connectedClients.clear();

        // Shut down thread pool
        clientThreadPool.shutdown();
        try {
            if (!clientThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                clientThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            clientThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Close server socket
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Error closing server socket", e);
        }

        logger.info("Game server stopped.");
    }

    // ─── Connection Management ───────────────────────────────────────

    /**
     * Main connection acceptance loop. Runs on dedicated thread.
     * Accepts incoming client connections and spawns handler threads.
     */
    private void acceptConnections() {
        try {
            serverSocket = new ServerSocket(port);
            logger.info("Listening for connections on port {}", port);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    logger.info("New connection from {}", clientSocket.getInetAddress());

                    if (connectedClients.size() >= maxClients) {
                        logger.warn("Server full, rejecting connection");
                        clientSocket.close();
                        continue;
                    }

                    // Create client handler and submit to thread pool
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    clientThreadPool.submit(handler);

                } catch (IOException e) {
                    if (running) {
                        logger.error("Error accepting connection", e);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Could not start server on port {}", port, e);
        }
    }

    /**
     * Register a client after successful authentication.
     * @param playerId the authenticated player's ID
     * @param handler the client handler managing this connection
     */
    public synchronized void registerClient(String playerId, ClientHandler handler) {
        // Disconnect existing session if player reconnects
        ClientHandler existing = connectedClients.get(playerId);
        if (existing != null) {
            logger.info("Player {} reconnecting, closing old session", playerId);
            existing.disconnect();
        }

        connectedClients.put(playerId, handler);
        logger.info("Player {} registered. Total online: {}", playerId, connectedClients.size());

        // Update player online status in database
        playerService.setPlayerOnline(playerId, true);
    }

    /**
     * Unregister a client on disconnect.
     */
    public synchronized void unregisterClient(String playerId) {
        connectedClients.remove(playerId);
        logger.info("Player {} disconnected. Total online: {}", playerId, connectedClients.size());

        playerService.setPlayerOnline(playerId, false);
    }

    // ─── Message Routing ─────────────────────────────────────────────

    /**
     * Process an incoming message from a client.
     * Routes to the appropriate service based on message type.
     */
    public void processMessage(GameMessage message, ClientHandler sender) {
        logger.debug("Processing message: {}", message);

        // Allow unauthenticated access only for login/register
        if (message.getType() != GameMessage.MessageType.JOIN_GAME &&
            message.getType() != GameMessage.MessageType.REGISTER &&
            message.getType() != GameMessage.MessageType.HEARTBEAT) {
            if (sender.getPlayerId() == null) {
                sender.sendMessage(GameMessage.error("Not authenticated"));
                return;
            }
        }

        switch (message.getType()) {
            case JOIN_GAME -> handleJoinGame(message, sender);
            case LEAVE_GAME -> handleLeaveGame(message, sender);
            case REGISTER -> handleRegister(message, sender);
            case UPDATE_BASE -> handleUpdateBase(message, sender);
            case PLACE_BUILDING -> handlePlaceBuilding(message, sender);
            case REMOVE_BUILDING -> handleRemoveBuilding(message, sender);
            case MOVE_BUILDING -> handleMoveBuilding(message, sender);
            case UPGRADE_BUILDING -> handleUpgradeBuilding(message, sender);
            case TRAIN_UNIT -> handleTrainUnit(message, sender);
            case UPGRADE_UNIT -> handleUpgradeUnit(message, sender);
            case ATTACK_PLAYER -> handleAttackPlayer(message, sender);
            case DEPLOY_UNIT -> handleDeployUnit(message, sender);
            case CHAT_MESSAGE -> handleChatMessage(message);
            case MATCHMAKING -> handleMatchmaking(message, sender);
            case LEADERBOARD -> handleLeaderboard(message, sender);
            case SPEED_UP -> handleSpeedUp(message, sender);
            case BATTLE_HISTORY -> handleBattleHistory(message, sender);
            case HEARTBEAT -> { /* no-op, keeps connection alive */ }
            default -> sender.sendMessage(GameMessage.error("Unknown message type: " + message.getType()));
        }
    }

    // ─── Message Handlers ────────────────────────────────────────────

    private void handleJoinGame(GameMessage message, ClientHandler sender) {
        // Authenticate player from payload (JSON: {username, passwordHash})
        String result = playerService.authenticatePlayer(message.getPayload());
        if (result != null) {
            sender.setPlayerId(result);
            registerClient(result, sender);
            // Send player ID first
            sender.sendMessage(new GameMessage(
                    GameMessage.MessageType.LOGIN_SUCCESS, "SERVER", result));
            // Then send full player state as base update
            String playerData = playerService.getPlayerDataJson(result);
            if (playerData != null) {
                sender.sendMessage(new GameMessage(
                        GameMessage.MessageType.UPDATE_BASE, "SERVER", playerData));
            }
        } else {
            sender.sendMessage(new GameMessage(
                    GameMessage.MessageType.LOGIN_FAILED, "SERVER", "Invalid credentials"));
        }
    }

    private void handleRegister(GameMessage message, ClientHandler sender) {
        String result = playerService.registerPlayer(message.getPayload());
        if (result != null) {
            sender.setPlayerId(result);
            registerClient(result, sender);
            sender.sendMessage(new GameMessage(
                    GameMessage.MessageType.LOGIN_SUCCESS, "SERVER", result));            // Send full player state as base update
            String playerData = playerService.getPlayerDataJson(result);
            if (playerData != null) {
                sender.sendMessage(new GameMessage(
                        GameMessage.MessageType.UPDATE_BASE, "SERVER", playerData));
            }        } else {
            sender.sendMessage(GameMessage.error("Registration failed – username may already exist"));
        }
    }

    private void handleLeaveGame(GameMessage message, ClientHandler sender) {
        if (sender.getPlayerId() != null) {
            // Check if player is in an active battle — cancel it
            String payload = message.getPayload();
            if (payload != null && !payload.isEmpty()) {
                // payload may contain a battleId for surrender
                battleService.getGameStateManager().cancelBattle(payload);
            }
            unregisterClient(sender.getPlayerId());
        }
        sender.disconnect();
    }

    private void handleUpdateBase(GameMessage message, ClientHandler sender) {
        playerService.updateBase(sender.getPlayerId(), message.getPayload());
    }

    private void handleRemoveBuilding(GameMessage message, ClientHandler sender) {
        String result = playerService.removeBuilding(sender.getPlayerId(), message.getPayload());
        if (result != null && result.contains("\"success\":true")) {
            String playerData = playerService.getPlayerDataJson(sender.getPlayerId());
            if (playerData != null) {
                sender.sendMessage(new GameMessage(
                        GameMessage.MessageType.UPDATE_BASE, "SERVER", playerData));
            }
        } else {
            sender.sendMessage(GameMessage.error(result != null ? result : "Remove failed"));
        }
    }

    private void handleMoveBuilding(GameMessage message, ClientHandler sender) {
        String result = playerService.moveBuilding(sender.getPlayerId(), message.getPayload());
        if (result != null && result.contains("\"success\":true")) {
            String playerData = playerService.getPlayerDataJson(sender.getPlayerId());
            if (playerData != null) {
                sender.sendMessage(new GameMessage(
                        GameMessage.MessageType.UPDATE_BASE, "SERVER", playerData));
            }
        } else {
            sender.sendMessage(GameMessage.error(result != null ? result : "Move failed"));
        }
    }

    private void handlePlaceBuilding(GameMessage message, ClientHandler sender) {
        String result = playerService.placeBuilding(sender.getPlayerId(), message.getPayload());
        if (result != null && result.contains("\"success\":true")) {
            // Send full player data so client sees the new building immediately
            String playerData = playerService.getPlayerDataJson(sender.getPlayerId());
            if (playerData != null) {
                sender.sendMessage(new GameMessage(
                        GameMessage.MessageType.UPDATE_BASE, "SERVER", playerData));
            }
        } else {
            sender.sendMessage(GameMessage.error(result != null ? result : "Build failed"));
        }
    }

    private void handleUpgradeBuilding(GameMessage message, ClientHandler sender) {
        String result = playerService.upgradeBuilding(sender.getPlayerId(), message.getPayload());
        if (result != null && result.contains("\"success\":true")) {
            // Send full player data so client sees the upgrade immediately
            String playerData = playerService.getPlayerDataJson(sender.getPlayerId());
            if (playerData != null) {
                sender.sendMessage(new GameMessage(
                        GameMessage.MessageType.UPDATE_BASE, "SERVER", playerData));
            }
        } else {
            sender.sendMessage(GameMessage.error(result != null ? result : "Upgrade failed"));
        }
    }

    private void handleTrainUnit(GameMessage message, ClientHandler sender) {
        String result = playerService.trainUnit(sender.getPlayerId(), message.getPayload());
        sender.sendMessage(new GameMessage(
                GameMessage.MessageType.UNIT_TRAINED, "SERVER", result));
        // Also send full player data so gold/elixir and army update correctly
        if (result != null && result.contains("\"success\":true")) {
            String playerData = playerService.getPlayerDataJson(sender.getPlayerId());
            if (playerData != null) {
                sender.sendMessage(new GameMessage(
                        GameMessage.MessageType.UPDATE_BASE, "SERVER", playerData));
            }
        }
    }

    private void handleUpgradeUnit(GameMessage message, ClientHandler sender) {
        String result = playerService.upgradeUnit(sender.getPlayerId(), message.getPayload());
        sender.sendMessage(new GameMessage(
                GameMessage.MessageType.UNIT_UPGRADED, "SERVER", result));
        // Send full player data so troop levels update on client
        if (result != null && result.contains("\"success\":true")) {
            String playerData = playerService.getPlayerDataJson(sender.getPlayerId());
            if (playerData != null) {
                sender.sendMessage(new GameMessage(
                        GameMessage.MessageType.UPDATE_BASE, "SERVER", playerData));
            }
        }
    }

    private void handleAttackPlayer(GameMessage message, ClientHandler sender) {
        String defenderId = message.getReceiverId();
        if (defenderId == null || defenderId.isEmpty()) {
            sender.sendMessage(GameMessage.error("No defender specified"));
            return;
        }
        String battleId = battleService.initiateBattle(sender.getPlayerId(), defenderId, message.getPayload());

        if (battleId == null) {
            sender.sendMessage(GameMessage.error("Could not start battle"));
            return;
        }

        // Build defender base JSON for the attacker's battle screen
        String battleStartPayload = battleId;
        com.clashgame.server.game.BattleEngine engine = 
            battleService.getGameStateManager().getBattleEngine(battleId);
        if (engine != null) {
            battleStartPayload = engine.buildDefenderBaseJson();
        }

        // Notify attacker that battle is starting (with defender base data)
        sender.sendMessage(new GameMessage(
                GameMessage.MessageType.BATTLE_START, "SERVER", battleStartPayload));

        // Notify defender (if online) with just the battle ID
        sendToPlayer(defenderId, new GameMessage(
                GameMessage.MessageType.BATTLE_START, "SERVER", battleId));
    }

    private void handleDeployUnit(GameMessage message, ClientHandler sender) {
        battleService.deployUnit(sender.getPlayerId(), message.getPayload());
    }

    private void handleChatMessage(GameMessage message) {
        // Sanitize: limit length, strip control characters
        String payload = message.getPayload();
        if (payload == null || payload.trim().isEmpty()) return;
        if (payload.length() > 200) payload = payload.substring(0, 200);
        payload = payload.replaceAll("[\\p{Cntrl}&&[^\\n]]", "").trim();
        if (payload.isEmpty()) return;
        message.setPayload(payload);
        // Broadcast to all connected clients
        broadcastMessage(message);
    }

    private void handleMatchmaking(GameMessage message, ClientHandler sender) {
        String opponentId = matchmakingService.findOpponent(sender.getPlayerId());
        if (opponentId != null) {
            sender.sendMessage(new GameMessage(
                    GameMessage.MessageType.MATCH_FOUND, "SERVER", opponentId));
        } else {
            sender.sendMessage(GameMessage.error("No suitable opponent found"));
        }
    }

    private void handleLeaderboard(GameMessage message, ClientHandler sender) {
        String leaderboard = playerService.getLeaderboard();
        sender.sendMessage(new GameMessage(
                GameMessage.MessageType.LEADERBOARD, "SERVER", leaderboard));
    }

    private void handleSpeedUp(GameMessage message, ClientHandler sender) {
        String payload = message.getPayload();
        if (payload == null) {
            sender.sendMessage(GameMessage.error("No speed-up data"));
            return;
        }
        
        // Determine if it's a building upgrade or training speed-up
        try {
            com.fasterxml.jackson.databind.JsonNode node = 
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);
            
            if (node.has("buildingId")) {
                // Speed up building upgrade
                String result = playerService.speedUpUpgrade(sender.getPlayerId(), payload);
                if (result != null && result.contains("\"success\":true")) {
                    String playerData = playerService.getPlayerDataJson(sender.getPlayerId());
                    if (playerData != null) {
                        sender.sendMessage(new GameMessage(
                                GameMessage.MessageType.UPDATE_BASE, "SERVER", playerData));
                    }
                } else {
                    sender.sendMessage(GameMessage.error(result != null ? result : "Speed-up failed"));
                }
            } else if (node.has("training") && node.get("training").asBoolean()) {
                // Speed up training queue
                int gemCost = battleService.getGameStateManager().speedUpTraining(sender.getPlayerId());
                if (gemCost > 0) {
                    com.clashgame.server.model.Player player = playerService.getPlayerById(sender.getPlayerId());
                    if (player != null && player.spendGems(gemCost)) {
                        // Save player with spent gems
                        // Gems already deducted; send updated data
                        String playerData = playerService.getPlayerDataJson(sender.getPlayerId());
                        if (playerData != null) {
                            sender.sendMessage(new GameMessage(
                                    GameMessage.MessageType.UPDATE_BASE, "SERVER", playerData));
                        }
                    } else {
                        sender.sendMessage(GameMessage.error("Not enough gems for training speed-up"));
                    }
                } else {
                    sender.sendMessage(GameMessage.error("No training in progress"));
                }
            }
        } catch (Exception e) {
            sender.sendMessage(GameMessage.error("Invalid speed-up request"));
        }
    }

    private void handleBattleHistory(GameMessage message, ClientHandler sender) {
        String history = playerService.getBattleHistory(sender.getPlayerId());
        sender.sendMessage(new GameMessage(
                GameMessage.MessageType.BATTLE_HISTORY, "SERVER", history));
    }

    // ─── Utility Methods ─────────────────────────────────────────────

    /**
     * Send a message to a specific player (if online).
     */
    public void sendToPlayer(String playerId, GameMessage message) {
        ClientHandler handler = connectedClients.get(playerId);
        if (handler != null) {
            handler.sendMessage(message);
        }
    }

    /**
     * Broadcast a message to all connected clients.
     */
    public void broadcastMessage(GameMessage message) {
        connectedClients.values().forEach(handler -> handler.sendMessage(message));
    }

    /**
     * Broadcast a message to all clients except the sender.
     */
    public void broadcastExcept(GameMessage message, String excludePlayerId) {
        connectedClients.forEach((id, handler) -> {
            if (!id.equals(excludePlayerId)) {
                handler.sendMessage(message);
            }
        });
    }

    /**
     * Get the number of currently connected clients.
     */
    public int getOnlinePlayerCount() {
        return connectedClients.size();
    }

    /**
     * Check if a specific player is online.
     */
    public boolean isPlayerOnline(String playerId) {
        return connectedClients.containsKey(playerId);
    }
}
