package com.clashgame.server.game;

/**
 * Observer interface for game events (Observer Design Pattern).
 * 
 * Components can implement this interface to be notified of important
 * game state changes without tight coupling to the source.
 * 
 * Design Pattern: OBSERVER
 * - Subject: GameStateManager (maintains list of listeners)
 * - Observers: Any component that registers via addGameEventListener()
 * 
 * This decouples event producers from consumers and follows the
 * OPEN/CLOSED principle — new event types can be added without
 * modifying existing listeners.
 */
public interface GameEventListener {

    /**
     * Called when a building upgrade completes.
     * @param playerId the player who owns the building
     * @param buildingId the building that was upgraded
     * @param newLevel the new level after upgrade
     */
    default void onBuildingUpgradeComplete(String playerId, long buildingId, int newLevel) {}

    /**
     * Called when a unit finishes training.
     * @param playerId the player who trained the unit
     * @param unitType the type of unit trained
     * @param count the number of units trained
     */
    default void onUnitTrainingComplete(String playerId, String unitType, int count) {}

    /**
     * Called when a battle starts.
     * @param battleId the unique battle identifier
     * @param attackerId the attacking player's ID
     * @param defenderId the defending player's ID
     */
    default void onBattleStarted(String battleId, String attackerId, String defenderId) {}

    /**
     * Called when a battle ends.
     * @param battleId the unique battle identifier
     * @param winnerId the winning player's ID (or null for draw)
     * @param stars stars earned by attacker
     */
    default void onBattleEnded(String battleId, String winnerId, int stars) {}

    /**
     * Called when a player's resources change significantly.
     * @param playerId the affected player's ID
     * @param gold current gold amount
     * @param elixir current elixir amount
     */
    default void onResourcesChanged(String playerId, int gold, int elixir) {}
}
