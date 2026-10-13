package com.clashgame.server.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a player in the game.
 * Central entity that owns a base, army, and resources.
 * 
 * Relationships:
 * - COMPOSITION: Player HAS-A Base (1:1, base cannot exist without player)
 * - AGGREGATION: Player HAS-A List<Unit> (army units can exist independently)
 * - ASSOCIATION: Player participates in Battle (M:N through battles)
 */
@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private int level;

    @Column(name = "xp", nullable = false)
    private int experiencePoints;

    @Column(nullable = false)
    private int gold;

    @Column(nullable = false)
    private int elixir;

    @Column(nullable = false)
    private int gems;

    @Column(nullable = false)
    private int trophies;

    @Column(name = "is_online", nullable = false)
    private boolean online;

    @Column(name = "clan_name")
    private String clanName;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Composition: Base belongs exclusively to this Player
    @OneToOne(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Base base;

    // Aggregation: Player's trained army units
    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Unit> army = new ArrayList<>();

    // Battles where this player was the attacker
    @OneToMany(mappedBy = "attacker", fetch = FetchType.LAZY)
    private List<Battle> attackBattles = new ArrayList<>();

    // Battles where this player was the defender
    @OneToMany(mappedBy = "defender", fetch = FetchType.LAZY)
    private List<Battle> defenseBattles = new ArrayList<>();

    // Troop upgrade levels (global per unit type, upgraded via Laboratory)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "player_troop_levels", joinColumns = @JoinColumn(name = "player_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "unit_type")
    @Column(name = "troop_level")
    private Map<UnitType, Integer> troopLevels = new HashMap<>();

    // Currently upgrading troop type in laboratory (null if none)
    @Enumerated(EnumType.STRING)
    @Column(name = "lab_upgrading_troop", length = 20)
    private UnitType labUpgradingTroop;

    @Column(name = "lab_upgrade_time_remaining")
    private int labUpgradeTimeRemaining;

    // ─── Constructors ────────────────────────────────────────────────

    public Player() {
        this.createdAt = LocalDateTime.now();
        this.level = 1;
        this.experiencePoints = 0;
        this.gold = 500;       // starting resources
        this.elixir = 500;
        this.gems = 50;
        this.trophies = 0;
        this.online = false;
    }

    public Player(String username, String passwordHash) {
        this();
        this.username = username;
        this.passwordHash = passwordHash;
    }

    // ─── Resource Management ─────────────────────────────────────────

    /**
     * Add gold to the player's reserves, capped by total storage capacity.
     * Base capacity: 5000, each GOLD_STORAGE adds 5000 * level.
     * @param amount amount to add (must be >= 0)
     */
    public void addGold(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Amount must be non-negative");
        this.gold += amount;
        int cap = getGoldCapacity();
        if (this.gold > cap) this.gold = cap;
    }

    /**
     * Spend gold from the player's reserves.
     * @param amount amount to spend
     * @return true if sufficient gold was available and spent
     */
    public boolean spendGold(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Amount must be non-negative");
        if (this.gold >= amount) {
            this.gold -= amount;
            return true;
        }
        return false;
    }

    /**
     * Add elixir to the player's reserves, capped by total storage capacity.
     * Base capacity: 5000, each ELIXIR_STORAGE adds 5000 * level.
     */
    public void addElixir(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Amount must be non-negative");
        this.elixir += amount;
        int cap = getElixirCapacity();
        if (this.elixir > cap) this.elixir = cap;
    }

    /**
     * Spend elixir from the player's reserves.
     * @return true if sufficient elixir was available and spent
     */
    public boolean spendElixir(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Amount must be non-negative");
        if (this.elixir >= amount) {
            this.elixir -= amount;
            return true;
        }
        return false;
    }

    /**
     * Add experience points and handle leveling up.
     */
    public void addExperience(int xp) {
        this.experiencePoints += xp;
        int xpThreshold = level * 100; // each level needs level*100 XP
        while (this.experiencePoints >= xpThreshold) {
            this.experiencePoints -= xpThreshold;
            this.level++;
            xpThreshold = level * 100;
        }
    }

    /**
     * Adjust trophies (can be positive or negative after battle).
     */
    public void adjustTrophies(int delta) {
        this.trophies = Math.max(0, this.trophies + delta);
    }

    /**
     * Spend gems from the player's reserves.
     * @param amount amount of gems to spend
     * @return true if sufficient gems were available and spent
     */
    public boolean spendGems(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Amount must be non-negative");
        if (this.gems >= amount) {
            this.gems -= amount;
            return true;
        }
        return false;
    }

    // ─── Army Management ─────────────────────────────────────────────

    /**
     * Add a trained unit to the player's army.
     */
    public void addUnit(Unit unit) {
        unit.setOwner(this);
        this.army.add(unit);
    }

    /**
     * Remove a unit from the army (e.g., after battle death).
     */
    public void removeUnit(Unit unit) {
        this.army.remove(unit);
        unit.setOwner(null);
    }

    /**
     * Get total army strength (sum of all unit attack values).
     */
    public int getTotalArmyStrength() {
        return army.stream().mapToInt(Unit::getAttackPower).sum();
    }

    // ─── Getters and Setters ─────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getExperiencePoints() { return experiencePoints; }
    public void setExperiencePoints(int experiencePoints) { this.experiencePoints = experiencePoints; }

    public int getGold() { return gold; }
    public void setGold(int gold) { this.gold = gold; }

    public int getElixir() { return elixir; }
    public void setElixir(int elixir) { this.elixir = elixir; }

    public int getGems() { return gems; }
    public void setGems(int gems) { this.gems = gems; }

    public int getTrophies() { return trophies; }
    public void setTrophies(int trophies) { this.trophies = trophies; }

    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }

    public String getClanName() { return clanName; }
    public void setClanName(String clanName) { this.clanName = clanName; }

    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public Base getBase() { return base; }
    public void setBase(Base base) { this.base = base; }

    public List<Unit> getArmy() { return army; }
    public void setArmy(List<Unit> army) { this.army = army; }

    public List<Battle> getAttackBattles() { return attackBattles; }
    public List<Battle> getDefenseBattles() { return defenseBattles; }

    public Map<UnitType, Integer> getTroopLevels() { return troopLevels; }
    public void setTroopLevels(Map<UnitType, Integer> troopLevels) { this.troopLevels = troopLevels; }

    /**
     * Get the current upgrade level for a specific troop type.
     * Returns 1 if never upgraded (default level).
     */
    public int getTroopLevel(UnitType type) {
        return troopLevels.getOrDefault(type, 1);
    }

    /**
     * Set the upgrade level for a specific troop type.
     */
    public void setTroopLevel(UnitType type, int level) {
        troopLevels.put(type, level);
    }

    public UnitType getLabUpgradingTroop() { return labUpgradingTroop; }
    public void setLabUpgradingTroop(UnitType labUpgradingTroop) { this.labUpgradingTroop = labUpgradingTroop; }

    public int getLabUpgradeTimeRemaining() { return labUpgradeTimeRemaining; }
    public void setLabUpgradeTimeRemaining(int labUpgradeTimeRemaining) { this.labUpgradeTimeRemaining = labUpgradeTimeRemaining; }

    @Override
    public String toString() {
        return "Player{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", level=" + level +
                ", trophies=" + trophies +
                ", gold=" + gold +
                ", elixir=" + elixir +
                '}';
    }

    // ─── Storage Capacity Helpers ────────────────────────────────────

    /**
     * Calculate total gold storage capacity.
     * Base capacity 5000, plus each GOLD_STORAGE building adds 5000 * its level.
     */
    public int getGoldCapacity() {
        int cap = 5000; // base capacity
        if (base != null) {
            for (Building b : base.getBuildings()) {
                if (b.getType() == BuildingType.GOLD_STORAGE) {
                    cap += 5000 * b.getLevel();
                }
            }
        }
        return cap;
    }

    /**
     * Calculate total elixir storage capacity.
     * Base capacity 5000, plus each ELIXIR_STORAGE building adds 5000 * its level.
     */
    public int getElixirCapacity() {
        int cap = 5000; // base capacity
        if (base != null) {
            for (Building b : base.getBuildings()) {
                if (b.getType() == BuildingType.ELIXIR_STORAGE) {
                    cap += 5000 * b.getLevel();
                }
            }
        }
        return cap;
    }
}
