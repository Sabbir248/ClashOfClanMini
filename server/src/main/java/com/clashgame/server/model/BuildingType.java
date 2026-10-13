package com.clashgame.server.model;

/**
 * Enumeration of all building types available in the game.
 * Each type has a category: RESOURCE, DEFENSE, ARMY, or STORAGE.
 */
public enum BuildingType {

    // ─── Resource Production Buildings ───────────────────────────────
    TOWN_HALL("Town Hall", BuildingCategory.CORE, 1, 0),
    GOLD_MINE("Gold Mine", BuildingCategory.RESOURCE, 5, 150),
    ELIXIR_COLLECTOR("Elixir Collector", BuildingCategory.RESOURCE, 5, 150),
    GOLD_STORAGE("Gold Storage", BuildingCategory.STORAGE, 3, 300),
    ELIXIR_STORAGE("Elixir Storage", BuildingCategory.STORAGE, 3, 300),

    // ─── Defense Buildings ───────────────────────────────────────────
    CANNON("Cannon", BuildingCategory.DEFENSE, 5, 250),
    ARCHER_TOWER("Archer Tower", BuildingCategory.DEFENSE, 5, 500),
    WALL("Wall", BuildingCategory.DEFENSE, 10, 50),
    MORTAR("Mortar", BuildingCategory.DEFENSE, 3, 800),

    // ─── Army Buildings ──────────────────────────────────────────────
    BARRACKS("Barracks", BuildingCategory.ARMY, 2, 200),
    ARMY_CAMP("Army Camp", BuildingCategory.ARMY, 2, 250),
    LABORATORY("Laboratory", BuildingCategory.ARMY, 1, 600);

    private final String displayName;
    private final BuildingCategory category;
    private final int maxCount; // max number per base
    private final int purchaseCost; // gold cost to build

    BuildingType(String displayName, BuildingCategory category, int maxCount, int purchaseCost) {
        this.displayName = displayName;
        this.category = category;
        this.maxCount = maxCount;
        this.purchaseCost = purchaseCost;
    }

    public String getDisplayName() { return displayName; }
    public BuildingCategory getCategory() { return category; }
    public int getMaxCount() { return maxCount; }
    public int getPurchaseCost() { return purchaseCost; }

    /**
     * Categories for grouping building types.
     */
    public enum BuildingCategory {
        CORE, RESOURCE, STORAGE, DEFENSE, ARMY
    }
}
