package com.clashgame.client.model;

import javafx.beans.property.*;
import javafx.scene.paint.Color;

/**
 * Client-side Building model with JavaFX properties.
 * Includes visual properties for rendering on the base grid.
 */
public class Building {

    private long id;
    private final StringProperty type = new SimpleStringProperty();
    private final IntegerProperty level = new SimpleIntegerProperty(1);
    private final IntegerProperty gridX = new SimpleIntegerProperty();
    private final IntegerProperty gridY = new SimpleIntegerProperty();
    private int width;
    private int height;
    private final IntegerProperty hitPoints = new SimpleIntegerProperty();
    private final IntegerProperty currentHitPoints = new SimpleIntegerProperty();
    private final IntegerProperty defenseValue = new SimpleIntegerProperty();
    private final IntegerProperty productionRate = new SimpleIntegerProperty();
    private final IntegerProperty storageCapacity = new SimpleIntegerProperty();
    private final BooleanProperty upgrading = new SimpleBooleanProperty(false);
    private final IntegerProperty upgradeTimeRemaining = new SimpleIntegerProperty(0);

    // Visual properties
    private Color color;
    private String displayLabel;

    // ─── Constructors ────────────────────────────────────────────────

    public Building() {}

    public Building(String type) {
        this.type.set(type);
        initializeFromType(type);
    }

    /**
     * Initialize visual and stat properties based on building type.
     */
    private void initializeFromType(String typeName) {
        switch (typeName) {
            case "TOWN_HALL" -> {
                width = 4; height = 4; hitPoints.set(1500); defenseValue.set(0);
                color = Color.GOLDENROD; displayLabel = "TH";
            }
            case "GOLD_MINE" -> {
                width = 2; height = 2; hitPoints.set(400); productionRate.set(10);
                color = Color.GOLD; displayLabel = "GM";
            }
            case "ELIXIR_COLLECTOR" -> {
                width = 2; height = 2; hitPoints.set(400); productionRate.set(10);
                color = Color.MEDIUMPURPLE; displayLabel = "EC";
            }
            case "GOLD_STORAGE" -> {
                width = 2; height = 2; hitPoints.set(600);
                color = Color.ORANGE; displayLabel = "GS";
            }
            case "ELIXIR_STORAGE" -> {
                width = 2; height = 2; hitPoints.set(600);
                color = Color.PLUM; displayLabel = "ES";
            }
            case "CANNON" -> {
                width = 2; height = 2; hitPoints.set(500); defenseValue.set(30);
                color = Color.DARKGRAY; displayLabel = "CA";
            }
            case "ARCHER_TOWER" -> {
                width = 2; height = 2; hitPoints.set(450); defenseValue.set(25);
                color = Color.BROWN; displayLabel = "AT";
            }
            case "WALL" -> {
                width = 1; height = 1; hitPoints.set(300); defenseValue.set(10);
                color = Color.SLATEGRAY; displayLabel = "W";
            }
            case "MORTAR" -> {
                width = 2; height = 2; hitPoints.set(400); defenseValue.set(40);
                color = Color.DARKRED; displayLabel = "MO";
            }
            case "BARRACKS" -> {
                width = 3; height = 3; hitPoints.set(500);
                color = Color.SADDLEBROWN; displayLabel = "BK";
            }
            case "ARMY_CAMP" -> {
                width = 3; height = 3; hitPoints.set(300);
                color = Color.OLIVE; displayLabel = "AC";
            }
            case "LABORATORY" -> {
                width = 3; height = 3; hitPoints.set(500);
                color = Color.TEAL; displayLabel = "LB";
            }
            default -> {
                width = 2; height = 2; hitPoints.set(100);
                color = Color.GRAY; displayLabel = "??";
            }
        }
        currentHitPoints.set(hitPoints.get());
    }

    // ─── Getters / Setters ───────────────────────────────────────────

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getType() { return type.get(); }
    public StringProperty typeProperty() { return type; }
    public void setType(String type) { this.type.set(type); initializeFromType(type); }

    public int getLevel() { return level.get(); }
    public IntegerProperty levelProperty() { return level; }
    public void setLevel(int level) { this.level.set(level); }

    public int getGridX() { return gridX.get(); }
    public IntegerProperty gridXProperty() { return gridX; }
    public void setGridX(int gridX) { this.gridX.set(gridX); }

    public int getGridY() { return gridY.get(); }
    public IntegerProperty gridYProperty() { return gridY; }
    public void setGridY(int gridY) { this.gridY.set(gridY); }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public int getHitPoints() { return hitPoints.get(); }
    public void setHitPoints(int hp) { this.hitPoints.set(hp); }
    public int getCurrentHitPoints() { return currentHitPoints.get(); }
    public void setCurrentHitPoints(int hp) { this.currentHitPoints.set(hp); }

    public int getDefenseValue() { return defenseValue.get(); }
    public void setDefenseValue(int value) { this.defenseValue.set(value); }
    public int getProductionRate() { return productionRate.get(); }
    public void setProductionRate(int value) { this.productionRate.set(value); }
    public int getStorageCapacity() { return storageCapacity.get(); }
    public void setStorageCapacity(int value) { this.storageCapacity.set(value); }

    public boolean isUpgrading() { return upgrading.get(); }
    public BooleanProperty upgradingProperty() { return upgrading; }
    public void setUpgrading(boolean upgrading) { this.upgrading.set(upgrading); }

    public int getUpgradeTimeRemaining() { return upgradeTimeRemaining.get(); }
    public IntegerProperty upgradeTimeRemainingProperty() { return upgradeTimeRemaining; }
    public void setUpgradeTimeRemaining(int seconds) { this.upgradeTimeRemaining.set(seconds); }

    public Color getColor() { return color; }
    public String getDisplayLabel() { return displayLabel; }

    @Override
    public String toString() {
        return "Building{" + type.get() + " lvl=" + level.get() + " at (" + gridX.get() + "," + gridY.get() + ")}";
    }
}
