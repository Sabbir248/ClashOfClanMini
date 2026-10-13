package com.clashgame.server.model;

/**
 * Interface for game entities that can be upgraded.
 * Demonstrates the INTERFACE SEGREGATION principle from SOLID.
 * 
 * Implementing classes: Building
 * 
 * This interface defines the contract for any game element that supports
 * a level-based upgrade mechanism with costs, timers, and prerequisites.
 */
public interface Upgradeable {

    /**
     * Get the current level of this upgradeable entity.
     * @return the current level (1-based)
     */
    int getLevel();

    /**
     * Start the upgrade process.
     * @param maxLevel the maximum level allowed
     * @return the gold cost of the upgrade, or -1 if cannot upgrade
     */
    int startUpgrade(int maxLevel);

    /**
     * Complete the upgrade, advancing the level and applying stat changes.
     */
    void completeUpgrade();

    /**
     * Calculate the gold cost for the next upgrade.
     * @return gold cost
     */
    int calculateUpgradeCost();

    /**
     * Calculate the time (in seconds) for the next upgrade.
     * @return upgrade duration in seconds
     */
    int calculateUpgradeTime();

    /**
     * Check if this entity is currently being upgraded.
     * @return true if upgrading
     */
    boolean isUpgrading();

    /**
     * Get the remaining time (seconds) for the current upgrade.
     * @return seconds remaining, or 0 if not upgrading
     */
    int getUpgradeTimeRemaining();
}
