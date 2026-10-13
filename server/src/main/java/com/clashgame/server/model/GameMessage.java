package com.clashgame.server.model;

import java.io.Serializable;

/**
 * Represents a message sent between client and server over the socket connection.
 * Implements Serializable for Java object stream transmission.
 * 
 * This is the core protocol object for all client-server communication.
 * 
 * Message types define the protocol:
 *   JOIN_GAME          - Player login / reconnection
 *   LEAVE_GAME         - Player disconnect
 *   REGISTER           - New player registration
 *   UPDATE_BASE        - Base layout change
 *   PLACE_BUILDING     - Place a building
 *   UPGRADE_BUILDING   - Start building upgrade
 *   TRAIN_UNIT         - Train a new unit
 *   ATTACK_PLAYER      - Initiate an attack
 *   BATTLE_UPDATE      - Battle tick update
 *   BATTLE_RESULT      - Final battle result
 *   RESOURCE_UPDATE    - Periodic resource update
 *   CHAT_MESSAGE       - In-game chat
 *   LEADERBOARD        - Request/receive leaderboard
 *   MATCHMAKING        - Find an opponent
 *   ERROR              - Server error notification
 *   SERVER_MESSAGE     - General server broadcast
 */
public class GameMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private MessageType type;
    private String senderId;
    private String receiverId;
    private String payload;      // JSON-encoded data payload
    private long timestamp;

    // ─── Constructors ────────────────────────────────────────────────

    public GameMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    public GameMessage(MessageType type, String senderId, String payload) {
        this();
        this.type = type;
        this.senderId = senderId;
        this.payload = payload;
    }

    public GameMessage(MessageType type, String senderId, String receiverId, String payload) {
        this(type, senderId, payload);
        this.receiverId = receiverId;
    }

    // ─── Factory Methods ─────────────────────────────────────────────

    public static GameMessage joinGame(String playerId, String credentials) {
        return new GameMessage(MessageType.JOIN_GAME, playerId, credentials);
    }

    public static GameMessage leaveGame(String playerId) {
        return new GameMessage(MessageType.LEAVE_GAME, playerId, "");
    }

    public static GameMessage attackPlayer(String attackerId, String defenderId, String armyData) {
        return new GameMessage(MessageType.ATTACK_PLAYER, attackerId, defenderId, armyData);
    }

    public static GameMessage battleUpdate(String battleData) {
        return new GameMessage(MessageType.BATTLE_UPDATE, "SERVER", battleData);
    }

    public static GameMessage battleResult(String resultData) {
        return new GameMessage(MessageType.BATTLE_RESULT, "SERVER", resultData);
    }

    public static GameMessage resourceUpdate(String playerId, String resourceData) {
        return new GameMessage(MessageType.RESOURCE_UPDATE, "SERVER", playerId, resourceData);
    }

    public static GameMessage chatMessage(String senderId, String message) {
        return new GameMessage(MessageType.CHAT_MESSAGE, senderId, message);
    }

    public static GameMessage error(String errorMessage) {
        return new GameMessage(MessageType.ERROR, "SERVER", errorMessage);
    }

    // ─── Getters and Setters ─────────────────────────────────────────

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "GameMessage{type=" + type + ", from=" + senderId +
               ", to=" + receiverId + ", payload=" + 
               (payload != null && payload.length() > 50 ? payload.substring(0, 50) + "..." : payload) + '}';
    }

    /**
     * All possible message types in the game protocol.
     */
    public enum MessageType {
        // Authentication
        JOIN_GAME,
        LEAVE_GAME,
        REGISTER,
        LOGIN_SUCCESS,
        LOGIN_FAILED,

        // Base Management
        UPDATE_BASE,
        PLACE_BUILDING,
        UPGRADE_BUILDING,
        REMOVE_BUILDING,
        MOVE_BUILDING,

        // Army
        TRAIN_UNIT,
        UNIT_TRAINED,
        UPGRADE_UNIT,
        UNIT_UPGRADED,

        // Battle
        ATTACK_PLAYER,
        BATTLE_START,
        BATTLE_UPDATE,
        BATTLE_RESULT,
        DEPLOY_UNIT,
        BATTLE_HISTORY,

        // Resources
        RESOURCE_UPDATE,

        // Gems / Speed-up
        SPEED_UP,

        // Social
        CHAT_MESSAGE,
        LEADERBOARD,

        // Matchmaking
        MATCHMAKING,
        MATCH_FOUND,

        // System
        ERROR,
        SERVER_MESSAGE,
        HEARTBEAT
    }
}
