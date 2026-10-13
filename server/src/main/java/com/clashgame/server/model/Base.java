package com.clashgame.server.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a player's base - the core territory containing buildings.
 * Uses a grid-based layout (e.g., 20x20) for building placement.
 * 
 * COMPOSITION relationship with Player: A Base cannot exist without its owner.
 * COMPOSITION relationship with Building: Buildings cannot exist outside a Base.
 */
@Entity
@Table(name = "bases")
public class Base {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "base_name", length = 100)
    private String baseName;

    // Grid dimensions for building placement
    @Column(name = "grid_width", nullable = false)
    private int gridWidth;

    @Column(name = "grid_height", nullable = false)
    private int gridHeight;

    // Total defense strength calculated from defensive buildings
    @Column(name = "defense_power")
    private int totalDefensePower;

    // Owner: Composition (cascade ALL, orphanRemoval)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false, unique = true)
    private Player owner;

    // Composition: Buildings belong exclusively to this Base
    @OneToMany(mappedBy = "base", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Building> buildings = new ArrayList<>();

    // ─── Constructors ────────────────────────────────────────────────

    public Base() {
        this.gridWidth = 20;
        this.gridHeight = 20;
        this.totalDefensePower = 0;
    }

    public Base(Player owner, String baseName) {
        this();
        this.owner = owner;
        this.baseName = baseName;
    }

    // ─── Building Management ─────────────────────────────────────────

    /**
     * Place a building at the specified grid position.
     * @param building the building to place
     * @param gridX x-coordinate on the base grid
     * @param gridY y-coordinate on the base grid
     * @return true if placement is valid and building was added
     */
    public boolean placeBuilding(Building building, int gridX, int gridY) {
        // Validate grid boundaries (building extent must fit within grid)
        if (gridX < 0 || gridY < 0 ||
            gridX + building.getWidth() > gridWidth ||
            gridY + building.getHeight() > gridHeight) {
            return false;
        }

        // Check for overlap with existing buildings
        for (Building existing : buildings) {
            if (isOverlapping(existing, gridX, gridY, building.getWidth(), building.getHeight())) {
                return false;
            }
        }

        building.setGridX(gridX);
        building.setGridY(gridY);
        building.setBase(this);
        buildings.add(building);
        recalculateDefensePower();
        return true;
    }

    /**
     * Remove a building from the base.
     */
    public boolean removeBuilding(Building building) {
        boolean removed = buildings.remove(building);
        if (removed) {
            building.setBase(null);
            recalculateDefensePower();
        }
        return removed;
    }

    /**
     * Check if a new building would overlap with an existing one.
     */
    private boolean isOverlapping(Building existing, int newX, int newY, int newW, int newH) {
        return newX < existing.getGridX() + existing.getWidth() &&
               newX + newW > existing.getGridX() &&
               newY < existing.getGridY() + existing.getHeight() &&
               newY + newH > existing.getGridY();
    }

    /**
     * Recalculate total defense power from all defensive buildings.
     */
    public void recalculateDefensePower() {
        this.totalDefensePower = buildings.stream()
                .filter(b -> b.getType() == BuildingType.CANNON ||
                             b.getType() == BuildingType.ARCHER_TOWER ||
                             b.getType() == BuildingType.WALL ||
                             b.getType() == BuildingType.MORTAR)
                .mapToInt(Building::getDefenseValue)
                .sum();
    }

    /**
     * Get all buildings of a specific type.
     */
    public List<Building> getBuildingsByType(BuildingType type) {
        return buildings.stream()
                .filter(b -> b.getType() == type)
                .toList();
    }

    /**
     * Calculate total hit points of all buildings (base health for battles).
     */
    public int getTotalHitPoints() {
        return buildings.stream().mapToInt(Building::getHitPoints).sum();
    }

    /**
     * Get total resource production rate (gold per minute).
     */
    public int getGoldProductionRate() {
        return (int) buildings.stream()
                .filter(b -> b.getType() == BuildingType.GOLD_MINE)
                .mapToDouble(Building::getProductionRate)
                .sum();
    }

    /**
     * Get total elixir production rate (elixir per minute).
     */
    public int getElixirProductionRate() {
        return (int) buildings.stream()
                .filter(b -> b.getType() == BuildingType.ELIXIR_COLLECTOR)
                .mapToDouble(Building::getProductionRate)
                .sum();
    }

    // ─── Getters and Setters ─────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBaseName() { return baseName; }
    public void setBaseName(String baseName) { this.baseName = baseName; }

    public int getGridWidth() { return gridWidth; }
    public void setGridWidth(int gridWidth) { this.gridWidth = gridWidth; }

    public int getGridHeight() { return gridHeight; }
    public void setGridHeight(int gridHeight) { this.gridHeight = gridHeight; }

    public int getBaseLevel() {
        return buildings.stream()
                .filter(b -> b.getType() == BuildingType.TOWN_HALL)
                .mapToInt(Building::getLevel)
                .findFirst().orElse(1);
    }
    public void setBaseLevel(int baseLevel) { /* derived from Town Hall level */ }

    public int getTotalDefensePower() { return totalDefensePower; }

    public Player getOwner() { return owner; }
    public void setOwner(Player owner) { this.owner = owner; }

    public List<Building> getBuildings() { return buildings; }
    public void setBuildings(List<Building> buildings) { this.buildings = buildings; }

    @Override
    public String toString() {
        return "Base{id=" + id + ", baseName='" + baseName + "'" +
               ", buildings=" + buildings.size() + ", defense=" + totalDefensePower + '}';
    }
}
