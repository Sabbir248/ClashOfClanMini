package com.clashgame.server.network;

import com.clashgame.server.model.GameMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;

/**
 * Handles communication with a single connected game client.
 * Each ClientHandler runs on its own thread from the server's thread pool.
 * 
 * Responsibilities:
 * - Read incoming messages from the client's ObjectInputStream
 * - Write outgoing messages to the client's ObjectOutputStream
 * - Route received messages to the GameServer for processing
 * - Handle disconnection and cleanup
 * 
 * Thread Safety:
 * - ObjectOutputStream writes are synchronized to prevent interleaved messages
 * - Uses volatile running flag for safe thread termination
 */
public class ClientHandler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);

    private final Socket socket;
    private final GameServer gameServer;

    private ObjectInputStream inputStream;
    private ObjectOutputStream outputStream;

    private String playerId; // Set after successful authentication
    private volatile boolean running;

    // ─── Constructor ─────────────────────────────────────────────────

    public ClientHandler(Socket socket, GameServer gameServer) {
        this.socket = socket;
        this.gameServer = gameServer;
        this.running = true;
    }

    // ─── Main Run Loop ───────────────────────────────────────────────

    @Override
    public void run() {
        try {
            // Initialize streams (order matters: output first to prevent deadlock)
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            outputStream.flush();
            inputStream = new ObjectInputStream(socket.getInputStream());

            logger.info("Client handler started for {}", socket.getInetAddress());

            // Main message processing loop
            while (running) {
                try {
                    // Blocking read for next message from client
                    GameMessage message = (GameMessage) inputStream.readObject();

                    if (message != null) {
                        logger.debug("Received from {}: {}", playerId, message.getType());
                        gameServer.processMessage(message, this);
                    }

                } catch (ClassNotFoundException e) {
                    logger.error("Invalid message format from client", e);
                    sendMessage(GameMessage.error("Invalid message format"));
                } catch (EOFException e) {
                    logger.info("Client {} disconnected (EOF)", playerId);
                    break;
                }
            }

        } catch (IOException e) {
            if (running) {
                logger.error("Connection error with client {}", playerId, e);
            }
        } finally {
            disconnect();
        }
    }

    // ─── Message Sending ─────────────────────────────────────────────

    /**
     * Send a message to this client.
     * Synchronized to prevent concurrent writes to the output stream.
     * @param message the GameMessage to send
     */
    public synchronized void sendMessage(GameMessage message) {
        if (!running || outputStream == null) return;

        try {
            outputStream.writeObject(message);
            outputStream.flush();
            // Reset to prevent memory leak with cached objects
            outputStream.reset();
            logger.debug("Sent to {}: {}", playerId, message.getType());
        } catch (IOException e) {
            logger.error("Failed to send message to {}", playerId, e);
            disconnect();
        }
    }

    // ─── Disconnect ──────────────────────────────────────────────────

    /**
     * Disconnect this client and clean up resources.
     */
    public void disconnect() {
        if (!running) return; // prevent double-disconnect
        running = false;

        logger.info("Disconnecting client: {}", playerId);

        // Unregister from server
        if (playerId != null) {
            gameServer.unregisterClient(playerId);
        }

        // Close streams and socket
        closeQuietly(inputStream);
        closeQuietly(outputStream);
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            logger.error("Error closing socket for {}", playerId, e);
        }
    }

    /**
     * Utility to close a stream without throwing.
     */
    private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // intentionally suppressed
            }
        }
    }

    // ─── Getters / Setters ───────────────────────────────────────────

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public boolean isRunning() { return running; }

    public Socket getSocket() { return socket; }
}
