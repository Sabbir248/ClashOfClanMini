package com.clashgame.server.model;

/**
 * Interface for game entities that can take damage and be destroyed.
 * Used by both buildings (defenses) and units during battle simulation.
 * 
 * Demonstrates POLYMORPHISM — the BattleEngine can damage any Damageable
 * entity without knowing whether it's a Building or Unit.
 * 
 * Implementing classes: Building, Unit
 */
public interface Damageable {

    /**
     * Apply damage to this entity.
     * @param damage the amount of damage to apply
     * @return true if the entity was destroyed (HP reached 0)
     */
    boolean takeDamage(int damage);

    /**
     * Get current hit points.
     * @return current HP
     */
    int getCurrentHitPoints();

    /**
     * Get maximum hit points.
     * @return max HP
     */
    int getMaxHitPoints();

    /**
     * Check if this entity has been destroyed.
     * @return true if HP <= 0
     */
    boolean isDestroyed();

    /**
     * Restore this entity to full health.
     */
    void repair();
}
