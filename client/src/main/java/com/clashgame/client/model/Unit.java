package com.clashgame.client.model;

import javafx.beans.property.*;

/**
 * Client-side Unit model with JavaFX properties for UI binding.
 */
public class Unit {

    private long id;
    private final StringProperty type = new SimpleStringProperty();
    private final IntegerProperty level = new SimpleIntegerProperty(1);
    private final IntegerProperty hitPoints = new SimpleIntegerProperty();
    private final IntegerProperty currentHitPoints = new SimpleIntegerProperty();
    private final IntegerProperty attackPower = new SimpleIntegerProperty();
    private final IntegerProperty speed = new SimpleIntegerProperty();
    private final IntegerProperty housingSpace = new SimpleIntegerProperty();
    private final BooleanProperty alive = new SimpleBooleanProperty(true);

    // Battle position
    private double battleX;
    private double battleY;

    // ─── Constructors ────────────────────────────────────────────────

    public Unit() {}

    public Unit(String type) {
        this.type.set(type);
        initializeFromType(type);
    }

    /**
     * Initialize unit stats from type name.
     */
    private void initializeFromType(String typeName) {
        switch (typeName) {
            case "BARBARIAN" -> {
                hitPoints.set(50); attackPower.set(20); speed.set(8); housingSpace.set(1);
            }
            case "ARCHER" -> {
                hitPoints.set(30); attackPower.set(15); speed.set(12); housingSpace.set(1);
            }
            case "GIANT" -> {
                hitPoints.set(200); attackPower.set(35); speed.set(4); housingSpace.set(2);
            }
            case "GOBLIN" -> {
                hitPoints.set(25); attackPower.set(10); speed.set(14); housingSpace.set(1);
            }
            case "WIZARD" -> {
                hitPoints.set(60); attackPower.set(50); speed.set(6); housingSpace.set(2);
            }
            case "DRAGON" -> {
                hitPoints.set(300); attackPower.set(80); speed.set(5); housingSpace.set(4);
            }
            case "HEALER" -> {
                hitPoints.set(80); attackPower.set(0); speed.set(6); housingSpace.set(2);
            }
            default -> {
                hitPoints.set(50); attackPower.set(10); speed.set(5); housingSpace.set(1);
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

    public int getHitPoints() { return hitPoints.get(); }
    public int getCurrentHitPoints() { return currentHitPoints.get(); }
    public void setCurrentHitPoints(int hp) { this.currentHitPoints.set(hp); }

    public int getAttackPower() { return attackPower.get(); }
    public int getSpeed() { return speed.get(); }
    public int getHousingSpace() { return housingSpace.get(); }

    public boolean isAlive() { return alive.get(); }
    public BooleanProperty aliveProperty() { return alive; }
    public void setAlive(boolean alive) { this.alive.set(alive); }

    public double getBattleX() { return battleX; }
    public void setBattleX(double battleX) { this.battleX = battleX; }
    public double getBattleY() { return battleY; }
    public void setBattleY(double battleY) { this.battleY = battleY; }

    @Override
    public String toString() {
        return type.get() + " (Lvl " + level.get() + ")";
    }
}
