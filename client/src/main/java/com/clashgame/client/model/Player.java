package com.clashgame.client.model;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Client-side Player model using JavaFX properties for data binding.
 * Properties automatically update the UI when values change.
 */
public class Player {

    private final LongProperty id = new SimpleLongProperty();
    private final StringProperty username = new SimpleStringProperty();
    private final IntegerProperty level = new SimpleIntegerProperty(1);
    private final IntegerProperty experiencePoints = new SimpleIntegerProperty(0);
    private final IntegerProperty gold = new SimpleIntegerProperty(500);
    private final IntegerProperty elixir = new SimpleIntegerProperty(500);
    private final IntegerProperty gems = new SimpleIntegerProperty(50);
    private final IntegerProperty trophies = new SimpleIntegerProperty(0);
    private final BooleanProperty online = new SimpleBooleanProperty(false);
    private final StringProperty clanName = new SimpleStringProperty();

    private Base base;
    private final ObservableList<Unit> army = FXCollections.observableArrayList();

    // ─── Constructors ────────────────────────────────────────────────

    public Player() {}

    public Player(String username) {
        this.username.set(username);
    }

    // ─── Property Accessors (for JavaFX binding) ─────────────────────

    public long getId() { return id.get(); }
    public LongProperty idProperty() { return id; }
    public void setId(long id) { this.id.set(id); }

    public String getUsername() { return username.get(); }
    public StringProperty usernameProperty() { return username; }
    public void setUsername(String username) { this.username.set(username); }

    public int getLevel() { return level.get(); }
    public IntegerProperty levelProperty() { return level; }
    public void setLevel(int level) { this.level.set(level); }

    public int getExperiencePoints() { return experiencePoints.get(); }
    public IntegerProperty experiencePointsProperty() { return experiencePoints; }
    public void setExperiencePoints(int xp) { this.experiencePoints.set(xp); }

    public int getGold() { return gold.get(); }
    public IntegerProperty goldProperty() { return gold; }
    public void setGold(int gold) { this.gold.set(gold); }

    public int getElixir() { return elixir.get(); }
    public IntegerProperty elixirProperty() { return elixir; }
    public void setElixir(int elixir) { this.elixir.set(elixir); }

    public int getGems() { return gems.get(); }
    public IntegerProperty gemsProperty() { return gems; }
    public void setGems(int gems) { this.gems.set(gems); }

    public int getTrophies() { return trophies.get(); }
    public IntegerProperty trophiesProperty() { return trophies; }
    public void setTrophies(int trophies) { this.trophies.set(trophies); }

    public boolean isOnline() { return online.get(); }
    public BooleanProperty onlineProperty() { return online; }
    public void setOnline(boolean online) { this.online.set(online); }

    public String getClanName() { return clanName.get(); }
    public StringProperty clanNameProperty() { return clanName; }
    public void setClanName(String clanName) { this.clanName.set(clanName); }

    public Base getBase() { return base; }
    public void setBase(Base base) { this.base = base; }

    public ObservableList<Unit> getArmy() { return army; }

    /**
     * Get total army capacity used.
     */
    public int getArmySize() {
        return army.stream().mapToInt(Unit::getHousingSpace).sum();
    }

    @Override
    public String toString() {
        return "Player{" + username.get() + ", lvl=" + level.get() + ", trophies=" + trophies.get() + "}";
    }
}
