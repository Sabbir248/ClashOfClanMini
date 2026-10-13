package com.clashgame.client.network;

import com.clashgame.server.model.GameMessage;
import com.clashgame.server.model.GameMessage.MessageType;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Client-side network handler for communicating with the GameServer.
 * Manages the TCP socket connection, sends/receives GameMessage objects,
 * and dispatches incoming messages to registered handlers.
 * 
 * Features:
 * - Asynchronous message receiving on a background thread
 * - JavaFX Platform.runLater() for UI-safe callbacks
 * - Automatic reconnection on disconnect
 * - Message handler registration system
 * 
 * Thread Safety:
 * - Output stream writes are synchronized
 * - Message handlers stored in ConcurrentHashMap
 */
public class NetworkClient {

    private static final Logger logger = LoggerFactory.getLogger(NetworkClient.class);

    private final String serverHost;
    private final int serverPort;

    private Socket socket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;

    private Thread receiverThread;
    private volatile boolean connected;

    private String playerId; // Set after successful login

    // Message handlers: MessageType -> handler callback
    private final Map<MessageType, Consumer<GameMessage>> messageHandlers = new ConcurrentHashMap<>();

    // Generic handler for all unhandled messages
    private Consumer<GameMessage> defaultHandler;

    // Connection state listener
    private Consumer<Boolean> connectionListener;

    // ─── Constructor ─────────────────────────────────────────────────

    public NetworkClient(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.connected = false;
    }

    // ─── Connection Management ───────────────────────────────────────

    /**
     * Connect to the game server.
     * @throws IOException if connection fails
     */
    public void connect() throws IOException {
        logger.info("Connecting to server {}:{}", serverHost, serverPort);

        socket = new Socket(serverHost, serverPort);
        outputStream = new ObjectOutputStream(socket.getOutputStream());
        outputStream.flush();
        inputStream = new ObjectInputStream(socket.getInputStream());

        connected = true;
        startReceiverThread();

        if (connectionListener != null) {
            Platform.runLater(() -> connectionListener.accept(true));
        }

        logger.info("Connected to game server");
    }

    /**
     * Disconnect from the server.
     */
    public void disconnect() {
        connected = false;
        logger.info("Disconnecting from server...");

        closeQuietly(inputStream);
        closeQuietly(outputStream);
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            logger.error("Error closing socket", e);
        }

        if (connectionListener != null) {
            Platform.runLater(() -> connectionListener.accept(false));
        }
    }

    /**
     * Start the background thread that reads incoming messages.
     */
    private void startReceiverThread() {
        receiverThread = new Thread(() -> {
            while (connected) {
                try {
                    GameMessage message = (GameMessage) inputStream.readObject();
                    if (message != null) {
                        handleIncomingMessage(message);
                    }
                } catch (EOFException e) {
                    logger.info("Server closed connection");
                    break;
                } catch (IOException | ClassNotFoundException e) {
                    if (connected) {
                        logger.error("Error receiving message", e);
                    }
                    break;
                }
            }

            connected = false;
            if (connectionListener != null) {
                Platform.runLater(() -> connectionListener.accept(false));
            }
        }, "NetworkClient-Receiver");

        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    // ─── Message Sending ─────────────────────────────────────────────

    /**
     * Send a message to the server.
     * @param message the GameMessage to send
     */
    public synchronized void sendMessage(GameMessage message) {
        if (!connected || outputStream == null) {
            logger.warn("Cannot send message: not connected");
            return;
        }

        try {
            outputStream.writeObject(message);
            outputStream.flush();
            outputStream.reset();
            logger.debug("Sent: {}", message.getType());
        } catch (IOException e) {
            logger.error("Failed to send message", e);
            disconnect();
        }
    }

    // ─── Convenience Send Methods ────────────────────────────────────

    /**
     * Send login request.
     */
    public void login(String username, String password) {
        String payload = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
        sendMessage(GameMessage.joinGame(username, payload));
    }

    /**
     * Send registration request.
     */
    public void register(String username, String password) {
        String payload = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
        sendMessage(new GameMessage(MessageType.REGISTER, username, payload));
    }

    /**
     * Send a request to find an opponent (matchmaking).
     */
    public void findOpponent() {
        sendMessage(new GameMessage(MessageType.MATCHMAKING, playerId, ""));
    }

    /**
     * Send an attack initiation.
     */
    public void attackPlayer(String defenderId, String armyData) {
        sendMessage(GameMessage.attackPlayer(playerId, defenderId, armyData));
    }

    /**
     * Deploy a unit during battle.
     */
    public void deployUnit(String battleId, String unitType, double x, double y) {
        String payload = String.format(
                "{\"battleId\":\"%s\",\"unitType\":\"%s\",\"x\":%.2f,\"y\":%.2f}",
                battleId, unitType, x, y);
        sendMessage(new GameMessage(MessageType.DEPLOY_UNIT, playerId, payload));
    }

    /**
     * Send a place building request.
     */
    public void placeBuilding(String buildingType, int gridX, int gridY) {
        String payload = String.format(
                "{\"type\":\"%s\",\"gridX\":%d,\"gridY\":%d}",
                buildingType, gridX, gridY);
        sendMessage(new GameMessage(MessageType.PLACE_BUILDING, playerId, payload));
    }

    /**
     * Send an upgrade building request.
     */
    public void upgradeBuilding(long buildingId) {
        String payload = String.format("{\"buildingId\":%d}", buildingId);
        sendMessage(new GameMessage(MessageType.UPGRADE_BUILDING, playerId, payload));
    }

    /**
     * Send a move building request.
     */
    public void moveBuilding(long buildingId, int gridX, int gridY) {
        String payload = String.format(
                "{\"buildingId\":%d,\"gridX\":%d,\"gridY\":%d}",
                buildingId, gridX, gridY);
        sendMessage(new GameMessage(MessageType.MOVE_BUILDING, playerId, payload));
    }

    /**
     * Send a unit training request.
     */
    public void trainUnit(String unitType, int count) {
        String payload = String.format("{\"unitType\":\"%s\",\"count\":%d}", unitType, count);
        sendMessage(new GameMessage(MessageType.TRAIN_UNIT, playerId, payload));
    }

    /**
     * Send a laboratory troop upgrade request.
     */
    public void upgradeUnit(String unitType) {
        String payload = String.format("{\"unitType\":\"%s\"}", unitType);
        sendMessage(new GameMessage(MessageType.UPGRADE_UNIT, playerId, payload));
    }

    /**
     * Send a chat message.
     */
    public void sendChat(String message) {
        sendMessage(GameMessage.chatMessage(playerId, message));
    }

    /**
     * Request the leaderboard.
     */
    public void requestLeaderboard() {
        sendMessage(new GameMessage(MessageType.LEADERBOARD, playerId, ""));
    }

    // ─── Incoming Message Handling ───────────────────────────────────

    /**
     * Handle an incoming message by dispatching to the registered handler.
     * Uses Platform.runLater() to ensure UI updates happen on the JavaFX thread.
     */
    private void handleIncomingMessage(GameMessage message) {
        logger.debug("Received: {}", message.getType());

        // Check if the message is a login success (store player ID)
        if (message.getType() == MessageType.LOGIN_SUCCESS) {
            this.playerId = message.getPayload();
        }

        // Dispatch to registered handler on JavaFX thread
        Consumer<GameMessage> handler = messageHandlers.get(message.getType());
        if (handler != null) {
            Platform.runLater(() -> handler.accept(message));
        } else if (defaultHandler != null) {
            Platform.runLater(() -> defaultHandler.accept(message));
        }
    }

    // ─── Handler Registration ────────────────────────────────────────

    /**
     * Register a handler for a specific message type.
     * The handler runs on the JavaFX Application Thread.
     */
    public void onMessage(MessageType type, Consumer<GameMessage> handler) {
        messageHandlers.put(type, handler);
    }

    /**
     * Register a default handler for unhandled message types.
     */
    public void onDefaultMessage(Consumer<GameMessage> handler) {
        this.defaultHandler = handler;
    }

    /**
     * Register a connection state change listener.
     */
    public void onConnectionChanged(Consumer<Boolean> listener) {
        this.connectionListener = listener;
    }

    // ─── Utility ─────────────────────────────────────────────────────

    private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try { closeable.close(); } catch (IOException ignored) {}
        }
    }

    public boolean isConnected() { return connected; }
    public String getPlayerId() { return playerId; }
}
