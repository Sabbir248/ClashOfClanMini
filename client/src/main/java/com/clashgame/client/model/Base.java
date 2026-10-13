package com.clashgame.client.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Client-side Base model. Represents the player's village with a grid layout.
 */
public class Base {

    private long id;
    private String baseName;
    private int gridWidth = 20;
    private int gridHeight = 20;
    private int baseLevel = 1;

    private final ObservableList<Building> buildings = FXCollections.observableArrayList();

    // Grid occupancy map: true if cell is occupied
    private final boolean[][] occupancyGrid;

    // ─── Constructors ────────────────────────────────────────────────

    public Base() {
        this.occupancyGrid = new boolean[gridWidth][gridHeight];
    }

    public Base(String baseName) {
        this();
        this.baseName = baseName;
    }

    // ─── Building Management ─────────────────────────────────────────

    /**
     * Check if a building can be placed at the given position.
     */
    public boolean canPlaceBuilding(int gridX, int gridY, int width, int height) {
        if (gridX < 0 || gridY < 0 ||
            gridX + width > gridWidth || gridY + height > gridHeight) {
            return false;
        }

        for (int x = gridX; x < gridX + width; x++) {
            for (int y = gridY; y < gridY + height; y++) {
                if (occupancyGrid[x][y]) return false;
            }
        }
        return true;
    }

    /**
     * Place a building and update the occupancy grid.
     */
    public boolean placeBuilding(Building building, int gridX, int gridY) {
        if (!canPlaceBuilding(gridX, gridY, building.getWidth(), building.getHeight())) {
            return false;
        }

        building.setGridX(gridX);
        building.setGridY(gridY);
        buildings.add(building);

        // Mark cells as occupied
        for (int x = gridX; x < gridX + building.getWidth(); x++) {
            for (int y = gridY; y < gridY + building.getHeight(); y++) {
                occupancyGrid[x][y] = true;
            }
        }
        return true;
    }

    /**
     * Force-place a building from server data, bypassing occupancy checks.
     * Used when rebuilding the base from authoritative server data.
     */
    public void placeBuildingForced(Building building, int gridX, int gridY) {
        building.setGridX(gridX);
        building.setGridY(gridY);
        buildings.add(building);

        // Mark cells as occupied (safe bounds check)
        for (int x = gridX; x < Math.min(gridX + building.getWidth(), gridWidth); x++) {
            for (int y = gridY; y < Math.min(gridY + building.getHeight(), gridHeight); y++) {
                if (x >= 0 && y >= 0) {
                    occupancyGrid[x][y] = true;
                }
            }
        }
    }

    /**
     * Remove a building and free the occupancy grid.
     */
    public void removeBuilding(Building building) {
        buildings.remove(building);
        for (int x = building.getGridX(); x < building.getGridX() + building.getWidth(); x++) {
            for (int y = building.getGridY(); y < building.getGridY() + building.getHeight(); y++) {
                occupancyGrid[x][y] = false;
            }
        }
    }

    /**
     * Get the total defense power.
     */
    public int getTotalDefensePower() {
        return buildings.stream().mapToInt(Building::getDefenseValue).sum();
    }

    // ─── Getters / Setters ───────────────────────────────────────────

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getBaseName() { return baseName; }
    public void setBaseName(String baseName) { this.baseName = baseName; }

    public int getGridWidth() { return gridWidth; }
    public int getGridHeight() { return gridHeight; }

    public int getBaseLevel() { return baseLevel; }
    public void setBaseLevel(int baseLevel) { this.baseLevel = baseLevel; }

    public ObservableList<Building> getBuildings() { return buildings; }
}
