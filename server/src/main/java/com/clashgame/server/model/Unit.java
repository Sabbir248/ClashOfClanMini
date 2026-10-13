package com.clashgame.server.model;

import jakarta.persistence.*;

/**
 * Represents a single military unit in a player's army.
 * Units are trained from the Barracks and deployed during battles.
 * 
 * AGGREGATION with Player: Units belong to a player's army but can
 * be destroyed independently during battle.
 * 
 * Implements Damageable for polymorphic damage handling in battle.
 */
@Entity
@Table(name = "units")
public class Unit implements Damageable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false, length = 20)
    private UnitType type;

    @Column(nullable = false)
    private int level;

    @Column(name = "max_hit_points", nullable = false)
    private int hitPoints;

    @Column(name = "hit_points", nullable = false)
    private int currentHitPoints;

    @Column(name = "attack_power", nullable = false)
    private int attackPower;

    @Column(name = "movement_speed", nullable = false)
    private double speed;

    @Column(name = "is_alive", nullable = false)
    private boolean alive;

    // Derived from UnitType (not persisted in DB)
    @Transient
    private int housingSpace;

    /**
     * Restore transient housingSpace when Hibernate loads from DB.
     */
    @PostLoad
    private void onLoad() {
        if (type != null) {
            this.housingSpace = type.getHousingSpace();
        }
    }

    // Position during battle (transient - not persisted)
    @Transient
    private double battleX;

    @Transient
    private double battleY;

    @Transient
    private Building targetBuilding;

    // Attack cooldown timer during battle (transient - not persisted)
    @Transient
    private double attackTimer = 0.0;

    // Aggregation: belongs to a player
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id")
    private Player owner;

    // ─── Constructors ────────────────────────────────────────────────

    public Unit() {
        this.level = 1;
        this.alive = true;
    }

    public Unit(UnitType type) {
        this();
        this.type = type;
        initializeFromType();
    }

    public Unit(UnitType type, int level) {
        this(type);
        this.level = level;
        scaleStatsToLevel();
    }

    /**
     * Initialize base stats from the UnitType enum.
     */
    private void initializeFromType() {
        this.hitPoints = type.getBaseHitPoints();
        this.currentHitPoints = this.hitPoints;
        this.attackPower = type.getBaseAttackPower();
        this.speed = type.getBaseSpeed();
        this.housingSpace = type.getHousingSpace();
    }

    /**
     * Scale unit stats based on level (each level = +10% stats).
     */
    public void scaleStatsToLevel() {
        double multiplier = 1.0 + (level - 1) * 0.10;
        this.hitPoints = (int) (type.getBaseHitPoints() * multiplier);
        this.currentHitPoints = this.hitPoints;
        this.attackPower = (int) (type.getBaseAttackPower() * multiplier);
    }

    // ─── Battle Actions ──────────────────────────────────────────────

    /**
     * Attack a building, dealing this unit's attack power as damage.
     * @param building the target building
     * @return true if the building was destroyed by this attack
     */
    public boolean attackBuilding(Building building) {
        if (!alive || building == null || building.isDestroyed()) {
            return false;
        }
        return building.takeDamage(this.attackPower);
    }

    /**
     * Take damage from a defensive building.
     * @param damage amount of damage received
     * @return true if this unit died
     */
    public boolean takeDamage(int damage) {
        if (!alive) return true;
        this.currentHitPoints = Math.max(0, this.currentHitPoints - damage);
        if (this.currentHitPoints <= 0) {
            this.alive = false;
            return true;
        }
        return false;
    }

    /**
     * Heal this unit by the specified amount (from Healer units).
     */
    public void heal(int amount) {
        if (!alive) return;
        this.currentHitPoints = Math.min(this.hitPoints, this.currentHitPoints + amount);
    }

    /**
     * Move the unit toward actual battle coordinates (simplified pathfinding).
     * @param targetX target x-coordinate
     * @param targetY target y-coordinate
     * @param deltaTime time step in seconds
     */
    public void moveToward(double targetX, double targetY, double deltaTime) {
        double dx = targetX - battleX;
        double dy = targetY - battleY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        if (distance > 0.5) { // not yet at target
            double moveAmount = speed * deltaTime;
            battleX += (dx / distance) * moveAmount;
            battleY += (dy / distance) * moveAmount;
        }
    }

    /**
     * Damageable interface: max hit points.
     */
    @Override
    public int getMaxHitPoints() {
        return this.hitPoints;
    }

    /**
     * Damageable interface: check if destroyed.
     */
    @Override
    public boolean isDestroyed() {
        return !this.alive;
    }

    /**
     * Damageable interface: repair (restore to full HP).
     */
    @Override
    public void repair() {
        this.currentHitPoints = this.hitPoints;
        this.alive = true;
    }

    /**
     * Reset unit to full health (after battle ends, for surviving units).
     */
    public void resetAfterBattle() {
        this.currentHitPoints = this.hitPoints;
        this.alive = true;
        this.battleX = 0;
        this.battleY = 0;
        this.targetBuilding = null;
    }

    /**
     * Calculate the elixir cost to train this unit.
     */
    public int getTrainingCost() {
        return type.getTrainingCostElixir() * level;
    }

    /**
     * Get the training time in seconds.
     */
    public int getTrainingTime() {
        return type.getTrainingTimeSeconds();
    }

    // ─── Getters and Setters ─────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UnitType getType() { return type; }
    public void setType(UnitType type) { this.type = type; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getHitPoints() { return hitPoints; }
    public void setHitPoints(int hitPoints) { this.hitPoints = hitPoints; }

    public int getCurrentHitPoints() { return currentHitPoints; }
    public void setCurrentHitPoints(int currentHitPoints) { this.currentHitPoints = currentHitPoints; }

    public int getAttackPower() { return attackPower; }
    public void setAttackPower(int attackPower) { this.attackPower = attackPower; }

    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }

    public int getHousingSpace() { return housingSpace; }

    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }

    public double getBattleX() { return battleX; }
    public void setBattleX(double battleX) { this.battleX = battleX; }

    public double getBattleY() { return battleY; }
    public void setBattleY(double battleY) { this.battleY = battleY; }

    public Building getTargetBuilding() { return targetBuilding; }
    public void setTargetBuilding(Building targetBuilding) { this.targetBuilding = targetBuilding; }

    public double getAttackTimer() { return attackTimer; }
    public void incrementAttackTimer(double dt) { this.attackTimer += dt; }
    public void resetAttackTimer() { this.attackTimer = 0.0; }

    public Player getOwner() { return owner; }
    public void setOwner(Player owner) { this.owner = owner; }

    @Override
    public String toString() {
        return "Unit{type=" + type + ", level=" + level +
               ", hp=" + currentHitPoints + "/" + hitPoints +
               ", atk=" + attackPower + ", alive=" + alive + '}';
    }
}
