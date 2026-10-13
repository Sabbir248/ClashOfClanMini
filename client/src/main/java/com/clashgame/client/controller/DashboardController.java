package com.clashgame.client.controller;

import com.clashgame.client.game.GameEngine;
import com.clashgame.client.model.Player;
import com.clashgame.client.model.Unit;
import com.clashgame.client.util.UIUtils;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.stream.Collectors;

/**
 * Controller for the main dashboard screen.
 * Displays player info, resources, and navigation to other screens.
 * 
 * FXML: dashboard_screen.fxml
 */
public class DashboardController {

    @FXML private Label usernameLabel;
    @FXML private Label levelLabel;
    @FXML private Label trophyLabel;
    @FXML private Label goldLabel;
    @FXML private Label elixirLabel;
    @FXML private Label gemsLabel;
    @FXML private Label armySizeLabel;
    @FXML private Label xpLabel;
    @FXML private Label goldCapLabel;
    @FXML private Label elixirCapLabel;
    @FXML private Button viewBaseButton;
    @FXML private Button attackButton;
    @FXML private Button trainButton;
    @FXML private Button leaderboardButton;
    @FXML private Button battleHistoryButton;
    @FXML private Button laboratoryButton;
    @FXML private Button logoutButton;

    private final GameEngine gameEngine = GameEngine.getInstance();

    // ─── Initialization ──────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Bind UI labels to player properties for automatic updates
        Player player = gameEngine.getCurrentPlayer();
        if (player != null) {
            usernameLabel.textProperty().bind(player.usernameProperty());
            levelLabel.textProperty().bind(player.levelProperty().asString("Level: %d"));
            trophyLabel.textProperty().bind(player.trophiesProperty().asString("Trophies: %d"));
            goldLabel.textProperty().bind(player.goldProperty().asString("%d"));
            elixirLabel.textProperty().bind(player.elixirProperty().asString("%d"));
            gemsLabel.textProperty().bind(player.gemsProperty().asString("%d"));

            // Update army size label with X/Y format
            updateArmySizeLabel();
            updateXpLabel();
            updateCapacityLabels();
            player.getArmy().addListener((javafx.collections.ListChangeListener<Unit>) c -> {
                updateArmySizeLabel();
            });
            player.experiencePointsProperty().addListener((obs, o, n) -> updateXpLabel());
            player.levelProperty().addListener((obs, o, n) -> updateXpLabel());
            player.goldProperty().addListener((obs, o, n) -> updateCapacityLabels());
            player.elixirProperty().addListener((obs, o, n) -> updateCapacityLabels());
        }
    }

    /** Calculate max army capacity from Army Camp buildings (uses server storageCapacity). */
    private int getMaxArmyCapacity() {
        Player player = gameEngine.getCurrentPlayer();
        if (player == null || player.getBase() == null) return 30;
        int total = player.getBase().getBuildings().stream()
                .filter(b -> "ARMY_CAMP".equals(b.getType()))
                .mapToInt(b -> b.getStorageCapacity() > 0 ? b.getStorageCapacity() : 30)
                .sum();
        return total > 0 ? total : 30;
    }

    /** Get Town Hall level for display. */
    private int getTownHallLevel() {
        Player player = gameEngine.getCurrentPlayer();
        if (player == null || player.getBase() == null) return 1;
        return player.getBase().getBuildings().stream()
                .filter(b -> "TOWN_HALL".equals(b.getType()))
                .mapToInt(com.clashgame.client.model.Building::getLevel)
                .findFirst().orElse(1);
    }

    private void updateArmySizeLabel() {
        Player player = gameEngine.getCurrentPlayer();
        if (player == null) return;
        int current = player.getArmySize();
        int max = getMaxArmyCapacity();
        int thLevel = getTownHallLevel();
        armySizeLabel.setText("🏠 TH" + thLevel + "  |  Army: " + current + "/" + max);
    }

    private void updateXpLabel() {
        Player player = gameEngine.getCurrentPlayer();
        if (player == null) return;
        int xp = player.getExperiencePoints();
        int needed = player.getLevel() * 100;
        xpLabel.setText("XP: " + xp + "/" + needed);
    }

    private void updateCapacityLabels() {
        Player player = gameEngine.getCurrentPlayer();
        if (player == null || player.getBase() == null) return;
        // Use server-sent storageCapacity if available, fallback to level * 5000
        int goldCap = 5000 + player.getBase().getBuildings().stream()
                .filter(b -> "GOLD_STORAGE".equals(b.getType()))
                .mapToInt(b -> b.getStorageCapacity() > 0 ? b.getStorageCapacity() : 5000 * b.getLevel())
                .sum();
        int elixirCap = 5000 + player.getBase().getBuildings().stream()
                .filter(b -> "ELIXIR_STORAGE".equals(b.getType()))
                .mapToInt(b -> b.getStorageCapacity() > 0 ? b.getStorageCapacity() : 5000 * b.getLevel())
                .sum();
        goldCapLabel.setText("/ " + goldCap);
        elixirCapLabel.setText("/ " + elixirCap);
    }

    // ─── Navigation ──────────────────────────────────────────────────

    @FXML
    private void onViewBaseClicked() {
        gameEngine.navigateTo("base");
    }

    @FXML
    private void onAttackClicked() {
        // Check for empty army before matchmaking
        Player player = gameEngine.getCurrentPlayer();
        if (player != null && player.getArmy().isEmpty()) {
            UIUtils.showWarning("No Army", "You have no troops! Train some units before attacking.");
            return;
        }
        attackButton.setText("⚔ Searching...");
        attackButton.setDisable(true);
        gameEngine.findOpponent();
        // Re-enable after 5 seconds in case no match found
        new Thread(() -> {
            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            javafx.application.Platform.runLater(() -> {
                attackButton.setText("⚔ Attack Player");
                attackButton.setDisable(false);
            });
        }).start();
    }

    @FXML
    private void onTrainClicked() {
        gameEngine.navigateTo("train");
    }

    @FXML
    private void onLeaderboardClicked() {
        gameEngine.navigateTo("leaderboard");
    }

    @FXML
    private void onBattleHistoryClicked() {
        gameEngine.navigateTo("history");
    }

    @FXML
    private void onLaboratoryClicked() {
        gameEngine.navigateTo("laboratory");
    }

    @FXML
    private void onLogoutClicked() {
        gameEngine.logout();
        gameEngine.navigateTo("login");
    }
}
