package com.clashgame.server.model;

/**
 * Enumeration of unit types available for training.
 * Each type has base stats that scale with level.
 */
public enum UnitType {

    //                              name         HP   ATK  SPD  HS  cost  time  maxLvl  labReq  upgCostBase  upgTimeBase
    BARBARIAN("Barbarian",          50,  20,  8,  1,  25,  10,  8,  1,   500,   30),
    ARCHER("Archer",                30,  15, 12,  1,  30,  15,  8,  1,   600,   40),
    GIANT("Giant",                 200,  35,  4,  2,  80,  30,  7,  2,  1200,   60),
    GOBLIN("Goblin",                25,  10, 14,  1,  15,   8,  8,  1,   400,   25),
    WIZARD("Wizard",                60,  50,  6,  2, 100,  40,  6,  3,  2000,   90),
    DRAGON("Dragon",               300,  80,  5,  4, 250, 100,  5,  4,  4000,  120),
    HEALER("Healer",                80,   0,  6,  2, 120,  50,  5,  3,  2500,   90);

    private final String displayName;
    private final int baseHitPoints;
    private final int baseAttackPower;
    private final int baseSpeed;            // movement speed (cells per second)
    private final int housingSpace;         // space used in army camp
    private final int trainingCostElixir;
    private final int trainingTimeSeconds;
    private final int maxLevel;             // max upgrade level
    private final int requiredLabLevel;     // minimum Laboratory level to upgrade
    private final int upgradeCostBase;      // base elixir cost per upgrade
    private final int upgradeTimeBase;      // base upgrade time in seconds

    UnitType(String displayName, int baseHitPoints, int baseAttackPower,
             int baseSpeed, int housingSpace, int trainingCostElixir, int trainingTimeSeconds,
             int maxLevel, int requiredLabLevel, int upgradeCostBase, int upgradeTimeBase) {
        this.displayName = displayName;
        this.baseHitPoints = baseHitPoints;
        this.baseAttackPower = baseAttackPower;
        this.baseSpeed = baseSpeed;
        this.housingSpace = housingSpace;
        this.trainingCostElixir = trainingCostElixir;
        this.trainingTimeSeconds = trainingTimeSeconds;
        this.maxLevel = maxLevel;
        this.requiredLabLevel = requiredLabLevel;
        this.upgradeCostBase = upgradeCostBase;
        this.upgradeTimeBase = upgradeTimeBase;
    }

    public String getDisplayName() { return displayName; }
    public int getBaseHitPoints() { return baseHitPoints; }
    public int getBaseAttackPower() { return baseAttackPower; }
    public int getBaseSpeed() { return baseSpeed; }
    public int getHousingSpace() { return housingSpace; }
    public int getTrainingCostElixir() { return trainingCostElixir; }
    public int getTrainingTimeSeconds() { return trainingTimeSeconds; }
    public int getMaxLevel() { return maxLevel; }
    public int getRequiredLabLevel() { return requiredLabLevel; }

    /**
     * Get the elixir cost to upgrade this troop TO a given target level.
     * Cost scales: upgradeCostBase * targetLevel
     */
    public int getUpgradeCost(int targetLevel) {
        return upgradeCostBase * targetLevel;
    }

    /**
     * Get the time in seconds to upgrade this troop TO a given target level.
     * Time scales: upgradeTimeBase * targetLevel
     */
    public int getUpgradeTime(int targetLevel) {
        return upgradeTimeBase * targetLevel;
    }

    /**
     * Get the hit points at a given level.
     * Each level adds 10% of base HP.
     */
    public int getHitPointsAtLevel(int level) {
        return (int)(baseHitPoints * (1.0 + (level - 1) * 0.10));
    }

    /**
     * Get the attack power at a given level.
     * Each level adds 10% of base attack.
     */
    public int getAttackPowerAtLevel(int level) {
        return (int)(baseAttackPower * (1.0 + (level - 1) * 0.10));
    }

    /**
     * Get the speed at a given level.
     * Each level adds 2% of base speed.
     */
    public int getSpeedAtLevel(int level) {
        return (int)(baseSpeed * (1.0 + (level - 1) * 0.02));
    }

    /**
     * Get the minimum Laboratory level required to upgrade TO a given target level.
     * Lab level requirement: requiredLabLevel + (targetLevel - 2)
     * e.g., Barbarian (labReq=1): Lv2 needs Lab1, Lv3 needs Lab2, Lv4 needs Lab3...
     */
    public int getRequiredLabLevelForUpgrade(int targetLevel) {
        return requiredLabLevel + (targetLevel - 2);
    }
}
