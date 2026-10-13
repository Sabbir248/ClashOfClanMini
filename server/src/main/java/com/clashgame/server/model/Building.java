package com.clashgame.server.model;

import jakarta.persistence.*;

/**
 * Represents a building placed on a player's base.
 * Buildings have types, levels, positions, and various stats.
 * 
 * COMPOSITION: Building belongs to exactly one Base.
 * INHERITANCE: Could be extended into specific building subclasses, 
 *              but we use BuildingType enum + stats for simplicity.
 * 
 * Implements:
 * - Upgradeable: supports level-based upgrade system with timers
 * - Damageable: can take damage and be destroyed in battle
 */
@Entity
@Table(name = "buildings")
public class Building implements Upgradeable, Damageable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "building_type", nullable = false, length = 30)
    private BuildingType type;

    @Column(nullable = false)
    private int level;

    // Grid position on the base
    @Column(name = "grid_x", nullable = false)
    private int gridX;

    @Column(name = "grid_y", nullable = false)
    private int gridY;

    // Size on the grid (in cells)
    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    // Building stats
    @Column(name = "max_hit_points", nullable = false)
    private int hitPoints;

    @Column(name = "hit_points", nullable = false)
    private int currentHitPoints;

    @Column(name = "defense_value")
    private int defenseValue;

    @Column(name = "production_rate")
    private double productionRate; // resources per minute (for resource buildings)

    @Column(name = "storage_capacity")
    private int storageCapacity;

    @Column(name = "is_upgrading", nullable = false)
    private boolean upgrading;

    @Column(name = "upgrade_time_remaining")
    private int upgradeTimeRemaining; // seconds remaining

    // Composition: belongs to one Base
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_id", nullable = false)
    private Base base;

    // Battle-only fields (not persisted)
    @Transient
    private double attackTimer = 0.0;

    // ─── Constructors ────────────────────────────────────────────────

    public Building() {
        this.level = 1;
        this.upgrading = false;
        this.upgradeTimeRemaining = 0;
    }

    public Building(BuildingType type) {
        this();
        this.type = type;
        initializeStats();
    }

    /**
     * Initialize building stats based on type and level.
     * This is a simplified stat system; a real game would use a data table.
     */
    private void initializeStats() {
        switch (type) {
            case TOWN_HALL -> {
                this.width = 4; this.height = 4;
                this.hitPoints = 1500; this.defenseValue = 0;
                this.productionRate = 0; this.storageCapacity = 0;
            }
            case GOLD_MINE -> {
                this.width = 2; this.height = 2;
                this.hitPoints = 400; this.defenseValue = 0;
                this.productionRate = 10; this.storageCapacity = 0; // 10 gold/min
            }
            case ELIXIR_COLLECTOR -> {
                this.width = 2; this.height = 2;
                this.hitPoints = 400; this.defenseValue = 0;
                this.productionRate = 10; this.storageCapacity = 0;
            }
            case GOLD_STORAGE -> {
                this.width = 2; this.height = 2;
                this.hitPoints = 600; this.defenseValue = 0;
                this.productionRate = 0; this.storageCapacity = 5000;
            }
            case ELIXIR_STORAGE -> {
                this.width = 2; this.height = 2;
                this.hitPoints = 600; this.defenseValue = 0;
                this.productionRate = 0; this.storageCapacity = 5000;
            }
            case CANNON -> {
                this.width = 2; this.height = 2;
                this.hitPoints = 500; this.defenseValue = 30;
                this.productionRate = 0; this.storageCapacity = 0;
            }
            case ARCHER_TOWER -> {
                this.width = 2; this.height = 2;
                this.hitPoints = 450; this.defenseValue = 25;
                this.productionRate = 0; this.storageCapacity = 0;
            }
            case WALL -> {
                this.width = 1; this.height = 1;
                this.hitPoints = 300; this.defenseValue = 10;
                this.productionRate = 0; this.storageCapacity = 0;
            }
            case MORTAR -> {
                this.width = 2; this.height = 2;
                this.hitPoints = 400; this.defenseValue = 40;
                this.productionRate = 0; this.storageCapacity = 0;
            }
            case BARRACKS -> {
                this.width = 3; this.height = 3;
                this.hitPoints = 500; this.defenseValue = 0;
                this.productionRate = 0; this.storageCapacity = 0;
            }
            case ARMY_CAMP -> {
                this.width = 3; this.height = 3;
                this.hitPoints = 300; this.defenseValue = 0;
                this.productionRate = 0; this.storageCapacity = 30; // troop capacity
            }
            case LABORATORY -> {
                this.width = 3; this.height = 3;
                this.hitPoints = 500; this.defenseValue = 0;
                this.productionRate = 0; this.storageCapacity = 0;
            }
        }
        this.currentHitPoints = this.hitPoints;
    }

    // ─── Upgrade System ──────────────────────────────────────────────

    /**
     * Start upgrading this building to the next level.
     * @param maxLevel max allowed level for the base level
     * @return gold cost of the upgrade, or -1 if cannot upgrade
     */
    public int startUpgrade(int maxLevel) {
        if (this.level >= maxLevel) {
            return -1;
        }
        this.upgrading = true;
        this.upgradeTimeRemaining = calculateUpgradeTime();
        return calculateUpgradeCost();
    }

    /**
     * Complete the upgrade: increase level and stats.
     */
    public void completeUpgrade() {
        if (!this.upgrading) return;
        this.level++;
        this.upgrading = false;
        this.upgradeTimeRemaining = 0;

        // Scale stats with level
        this.hitPoints = (int) (hitPoints * 1.2);
        this.currentHitPoints = this.hitPoints;
        if (defenseValue > 0) this.defenseValue = (int) (defenseValue * 1.15);
        if (productionRate > 0) this.productionRate = productionRate * 1.1;
        if (storageCapacity > 0) this.storageCapacity = (int) (storageCapacity * 1.25);
    }

    /**
     * Calculate gold cost to upgrade (simplified formula).
     */
    public int calculateUpgradeCost() {
        return level * 200 + 100;
    }

    /**
     * Calculate time to upgrade in seconds (simplified formula).
     */
    public int calculateUpgradeTime() {
        return level * 60; // each level = +60s
    }

    /**
     * Apply damage to this building during a battle.
     * @param damage the damage amount
     * @return true if the building was destroyed
     */
    public boolean takeDamage(int damage) {
        this.currentHitPoints = Math.max(0, this.currentHitPoints - damage);
        return this.currentHitPoints <= 0;
    }

    /**
     * Repair building to full health.
     */
    public void repair() {
        this.currentHitPoints = this.hitPoints;
    }

    /**
     * Check if this building is destroyed (0 HP).
     */
    public boolean isDestroyed() {
        return this.currentHitPoints <= 0;
    }

    /**
     * Damageable interface: max hit points (delegates to hitPoints field).
     */
    @Override
    public int getMaxHitPoints() {
        return this.hitPoints;
    }

    // ─── Getters and Setters ─────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public BuildingType getType() { return type; }
    public void setType(BuildingType type) { this.type = type; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getGridX() { return gridX; }
    public void setGridX(int gridX) { this.gridX = gridX; }

    public int getGridY() { return gridY; }
    public void setGridY(int gridY) { this.gridY = gridY; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public int getHitPoints() { return hitPoints; }
    public void setHitPoints(int hitPoints) { this.hitPoints = hitPoints; }

    public int getCurrentHitPoints() { return currentHitPoints; }
    public void setCurrentHitPoints(int currentHitPoints) { this.currentHitPoints = currentHitPoints; }

    public int getDefenseValue() { return defenseValue; }
    public void setDefenseValue(int defenseValue) { this.defenseValue = defenseValue; }

    public double getProductionRate() { return productionRate; }
    public void setProductionRate(double productionRate) { this.productionRate = productionRate; }

    public int getStorageCapacity() { return storageCapacity; }
    public void setStorageCapacity(int storageCapacity) { this.storageCapacity = storageCapacity; }

    public boolean isUpgrading() { return upgrading; }
    public void setUpgrading(boolean upgrading) { this.upgrading = upgrading; }

    public int getUpgradeTimeRemaining() { return upgradeTimeRemaining; }
    public void setUpgradeTimeRemaining(int upgradeTimeRemaining) { this.upgradeTimeRemaining = upgradeTimeRemaining; }

    public Base getBase() { return base; }
    public void setBase(Base base) { this.base = base; }

    public double getAttackTimer() { return attackTimer; }
    public void incrementAttackTimer(double dt) { this.attackTimer += dt; }
    public void resetAttackTimer() { this.attackTimer = 0.0; }

    @Override
    public String toString() {
        return "Building{type=" + type + ", level=" + level +
               ", pos=(" + gridX + "," + gridY + "), hp=" + currentHitPoints + "/" + hitPoints + '}';
    }
}
