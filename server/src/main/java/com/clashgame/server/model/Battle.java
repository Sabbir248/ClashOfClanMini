package com.clashgame.server.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a battle between two players.
 * Stores the outcome, star rating, and loot earned.
 * 
 * ASSOCIATION: Battle references attacker and defender Players.
 * A battle has a lifecycle: PENDING → IN_PROGRESS → COMPLETED.
 */
@Entity
@Table(name = "battles")
public class Battle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attacker_id", nullable = false)
    private Player attacker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "defender_id", nullable = false)
    private Player defender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BattleStatus status;

    // Battle results
    @Column(name = "stars_earned")
    private int starsEarned; // 0-3 stars

    @Column(name = "destruction_pct")
    private double destructionPercentage; // 0.0 - 100.0

    @Column(name = "gold_looted")
    private int goldLooted;

    @Column(name = "elixir_looted")
    private int elixirLooted;

    @Column(name = "trophies_won")
    private int trophiesWon;

    @Column(name = "trophies_lost")
    private int trophiesLost;

    @Column(name = "attacker_units_deployed")
    private int attackerUnitsDeployed;

    @Column(name = "attacker_units_lost")
    private int attackerUnitsLost;

    @Column(name = "buildings_destroyed")
    private int buildingsDestroyed;

    @Column(name = "total_buildings")
    private int totalBuildings;

    // Duration in seconds
    @Column(name = "battle_duration")
    private int battleDuration;

    @Column(name = "max_duration")
    private int maxDuration; // time limit (e.g., 180 seconds)

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Store the deployed unit types as a simple serialized list
    @ElementCollection
    @CollectionTable(name = "battle_deployed_units", joinColumns = @JoinColumn(name = "battle_id"))
    @Column(name = "unit_type")
    private List<String> deployedUnitTypes = new ArrayList<>();

    // ─── Constructors ────────────────────────────────────────────────

    public Battle() {
        this.createdAt = LocalDateTime.now();
        this.status = BattleStatus.PENDING;
        this.maxDuration = 180; // 3-minute battles
        this.starsEarned = 0;
        this.destructionPercentage = 0.0;
    }

    public Battle(Player attacker, Player defender) {
        this();
        this.attacker = attacker;
        this.defender = defender;
    }

    // ─── Battle Lifecycle ────────────────────────────────────────────

    /**
     * Start the battle.
     */
    public void start() {
        this.status = BattleStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * End the battle with results.
     */
    public void complete(double destructionPct, int buildingsDestroyed, int totalBuildings) {
        this.status = BattleStatus.COMPLETED;
        this.endedAt = LocalDateTime.now();
        this.destructionPercentage = destructionPct;
        this.buildingsDestroyed = buildingsDestroyed;
        this.totalBuildings = totalBuildings;
        this.starsEarned = calculateStars(destructionPct);
        calculateTrophyExchange();
        calculateLoot();
    }

    /**
     * Calculate stars based on destruction percentage.
     * 1 star = 50%+ destruction
     * 2 stars = 75%+ destruction 
     * 3 stars = 100% destruction
     */
    private int calculateStars(double destructionPct) {
        if (destructionPct >= 100.0) return 3;
        if (destructionPct >= 75.0) return 2;
        if (destructionPct >= 50.0) return 1;
        return 0;
    }

    /**
     * Calculate trophy exchange based on battle result.
     * Stars earned → trophies won by attacker, lost by defender.
     * 0 stars: attacker loses 10, defender gains 10.
     * 1 star: attacker +10, defender -10.
     * 2 stars: attacker +20, defender -20.
     * 3 stars: attacker +30, defender -30.
     */
    private void calculateTrophyExchange() {
        int baseTrophies = 30;
        if (starsEarned > 0) {
            this.trophiesWon = starsEarned * baseTrophies / 3;   // attacker gains
            this.trophiesLost = this.trophiesWon;                // defender loses same amount
        } else {
            this.trophiesWon = -10;   // attacker penalty (loses 10)
            this.trophiesLost = -10;  // defender gains 10 (negative loss = gain)
        }
    }

    /**
     * Calculate loot earned based on destruction percentage.
     */
    private void calculateLoot() {
        int defenderGold = defender.getGold();
        int defenderElixir = defender.getElixir();

        // Attacker can loot up to 20% of defender's resources, scaled by destruction
        double lootFraction = (destructionPercentage / 100.0) * 0.20;
        this.goldLooted = (int) (defenderGold * lootFraction);
        this.elixirLooted = (int) (defenderElixir * lootFraction);
    }

    /**
     * Check if battle time has expired.
     */
    public boolean isTimeExpired(int elapsedSeconds) {
        return elapsedSeconds >= maxDuration;
    }

    // ─── Getters and Setters ─────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Player getAttacker() { return attacker; }
    public void setAttacker(Player attacker) { this.attacker = attacker; }

    public Player getDefender() { return defender; }
    public void setDefender(Player defender) { this.defender = defender; }

    public BattleStatus getStatus() { return status; }
    public void setStatus(BattleStatus status) { this.status = status; }

    public int getStarsEarned() { return starsEarned; }
    public double getDestructionPercentage() { return destructionPercentage; }

    public int getGoldLooted() { return goldLooted; }
    public int getElixirLooted() { return elixirLooted; }

    public int getTrophiesWon() { return trophiesWon; }
    public int getTrophiesLost() { return trophiesLost; }

    public int getAttackerUnitsDeployed() { return attackerUnitsDeployed; }
    public void setAttackerUnitsDeployed(int n) { this.attackerUnitsDeployed = n; }

    public int getAttackerUnitsLost() { return attackerUnitsLost; }
    public void setAttackerUnitsLost(int n) { this.attackerUnitsLost = n; }

    public int getBuildingsDestroyed() { return buildingsDestroyed; }
    public int getTotalBuildings() { return totalBuildings; }

    public int getBattleDuration() { return battleDuration; }
    public void setBattleDuration(int battleDuration) { this.battleDuration = battleDuration; }

    public int getMaxDuration() { return maxDuration; }
    public void setMaxDuration(int maxDuration) { this.maxDuration = maxDuration; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public List<String> getDeployedUnitTypes() { return deployedUnitTypes; }
    public void setDeployedUnitTypes(List<String> types) { this.deployedUnitTypes = types; }

    @Override
    public String toString() {
        return "Battle{id=" + id + ", status=" + status +
               ", stars=" + starsEarned +
               ", destruction=" + destructionPercentage + "%}";
    }

    /**
     * Battle status lifecycle enum.
     */
    public enum BattleStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED
    }
}
