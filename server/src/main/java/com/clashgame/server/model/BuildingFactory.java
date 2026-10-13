package com.clashgame.server.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class for creating game buildings (Factory Design Pattern).
 * 
 * Encapsulates the complex construction logic for buildings, ensuring
 * each building is properly initialized with the correct stats for its type.
 * 
 * Design Pattern: FACTORY METHOD
 * - Instead of clients calling `new Building(type)` directly,
 *   they use `BuildingFactory.create(type)` which centralizes creation logic.
 * - This makes it easy to add new building types or change initialization
 *   without modifying all call sites.
 * 
 * Benefits:
 * - Single Responsibility: Creation logic lives in one place
 * - Open/Closed: New building types only require changes here
 * - Encapsulation: Callers don't need to know initialization details
 */
public class BuildingFactory {

    private static final Logger logger = LoggerFactory.getLogger(BuildingFactory.class);

    // Private constructor — static factory, not instantiable
    private BuildingFactory() {}

    /**
     * Create a new building of the specified type with default stats.
     * 
     * @param type the BuildingType to create
     * @return a fully initialized Building entity
     * @throws IllegalArgumentException if type is null
     */
    public static Building create(BuildingType type) {
        if (type == null) {
            throw new IllegalArgumentException("BuildingType cannot be null");
        }

        Building building = new Building(type);
        logger.debug("Created new {} (HP: {}, DEF: {}, PROD: {})",
                type.getDisplayName(), building.getHitPoints(),
                building.getDefenseValue(), building.getProductionRate());
        return building;
    }

    /**
     * Create a building at a specific grid position.
     * Convenience method combining creation and positioning.
     * 
     * @param type the BuildingType to create
     * @param gridX the X grid coordinate
     * @param gridY the Y grid coordinate
     * @return a fully initialized and positioned Building entity
     */
    public static Building createAt(BuildingType type, int gridX, int gridY) {
        Building building = create(type);
        building.setGridX(gridX);
        building.setGridY(gridY);
        return building;
    }

    /**
     * Create a building with a specific starting level.
     * Used for loading game state from database or creating test data.
     * 
     * @param type the BuildingType to create
     * @param level the starting level
     * @return a Building with stats scaled to the given level
     */
    public static Building createAtLevel(BuildingType type, int level) {
        Building building = create(type);
        // Simulate upgrades to reach the desired level
        for (int i = 1; i < level; i++) {
            building.setUpgrading(true);
            building.completeUpgrade();
        }
        return building;
    }

    /**
     * Get the maximum level a building can reach at a given Town Hall level.
     * Non-TH buildings are capped at TH level × 3.
     * 
     * @param type the building type
     * @param townHallLevel the current Town Hall level
     * @return the maximum allowed level
     */
    public static int getMaxLevel(BuildingType type, int townHallLevel) {
        if (type == BuildingType.TOWN_HALL) {
            return 10; // TH has no cap from itself
        }
        return townHallLevel * 3;
    }

    /**
     * Check if a building type is a defense building.
     * @param type the building type
     * @return true if it's a defense building
     */
    public static boolean isDefense(BuildingType type) {
        return type.getCategory() == BuildingType.BuildingCategory.DEFENSE;
    }

    /**
     * Check if a building type produces resources.
     * @param type the building type
     * @return true if it's a resource producer
     */
    public static boolean isResourceProducer(BuildingType type) {
        return type == BuildingType.GOLD_MINE || type == BuildingType.ELIXIR_COLLECTOR;
    }
}
