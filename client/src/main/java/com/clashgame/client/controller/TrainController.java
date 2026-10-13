package com.clashgame.client.controller;

import com.clashgame.client.game.GameEngine;
import com.clashgame.client.model.Player;
import com.clashgame.client.model.Unit;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Map;

/**
 * Controller for the unit training screen.
 * Card-based troop selection (same style as Laboratory).
 * 
 * FXML: train_screen.fxml
 */
public class TrainController {

    @FXML private FlowPane troopCardsPane;
    @FXML private VBox troopDetailCard;
    @FXML private VBox trainPanel;
    @FXML private Label selectedTroopEmoji;
    @FXML private Label selectedTroopName;
    @FXML private Label unitInfoLabel;
    @FXML private Label statsInfoLabel;
    @FXML private Spinner<Integer> countSpinner;
    @FXML private Button trainButton;
    @FXML private Label statusLabel;
    @FXML private Label elixirLabel;
    @FXML private Label armySizeLabel;
    @FXML private ListView<String> armyListView;
    @FXML private ListView<String> trainingQueueListView;
    @FXML private Button speedUpTrainingButton;

    private final GameEngine gameEngine = GameEngine.getInstance();
    private String selectedTroop = null;
    private VBox selectedCard = null;

    // Troop emojis (same as Laboratory)
    private static final Map<String, String> TROOP_EMOJIS = Map.of(
            "BARBARIAN", "⚔", "ARCHER", "🏹", "GIANT", "🛡",
            "GOBLIN", "💰", "WIZARD", "✨", "DRAGON", "🐉", "HEALER", "❤"
    );

    // Troop card accent colors (same as Laboratory)
    private static final Map<String, String> TROOP_COLORS = Map.of(
            "BARBARIAN", "#ff9800", "ARCHER", "#e91e63", "GIANT", "#795548",
            "GOBLIN", "#4caf50", "WIZARD", "#9c27b0", "DRAGON", "#f44336", "HEALER", "#e91e63"
    );

    // Troop roles (same as Laboratory)
    private static final Map<String, String> TROOP_ROLES = Map.of(
            "BARBARIAN", "⚔ Melee Attacker", "ARCHER", "🏹 Ranged Attacker",
            "GIANT", "🛡 Tank - Targets Defenses", "GOBLIN", "💰 Fast - Targets Resources",
            "WIZARD", "✨ Splash Damage", "DRAGON", "🐉 Flying Splash", "HEALER", "❤ Heals Friendly Units"
    );

    // Unit costs (elixir)
    private static final Map<String, Integer> UNIT_COSTS = Map.of(
            "BARBARIAN", 25, "ARCHER", 30, "GIANT", 80,
            "GOBLIN", 15, "WIZARD", 100, "DRAGON", 250, "HEALER", 120
    );

    private static final Map<String, Integer> UNIT_HOUSING = Map.of(
            "BARBARIAN", 1, "ARCHER", 1, "GIANT", 2,
            "GOBLIN", 1, "WIZARD", 2, "DRAGON", 4, "HEALER", 2
    );

    private static final Map<String, Integer> UNIT_HP = Map.of(
            "BARBARIAN", 50, "ARCHER", 30, "GIANT", 200,
            "GOBLIN", 25, "WIZARD", 60, "DRAGON", 300, "HEALER", 80
    );

    private static final Map<String, Integer> UNIT_ATK = Map.of(
            "BARBARIAN", 20, "ARCHER", 15, "GIANT", 35,
            "GOBLIN", 10, "WIZARD", 50, "DRAGON", 80, "HEALER", 0
    );

    private static final Map<String, Integer> UNIT_SPEED = Map.of(
            "BARBARIAN", 8, "ARCHER", 12, "GIANT", 4,
            "GOBLIN", 14, "WIZARD", 6, "DRAGON", 5, "HEALER", 6
    );

    // Barracks level → troop unlock tiers
    private static final Map<String, Integer> REQUIRED_BARRACKS_LEVEL = Map.of(
            "BARBARIAN", 1, "ARCHER", 1, "GOBLIN", 1,
            "GIANT", 2, "WIZARD", 2,
            "HEALER", 3, "DRAGON", 4
    );

    // Display order
    private static final String[] TROOP_ORDER = {
            "BARBARIAN", "ARCHER", "GOBLIN", "GIANT", "WIZARD", "HEALER", "DRAGON"
    };

    // ─── Initialization ──────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Count spinner (1 to 20)
        countSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, 1));
        countSpinner.valueProperty().addListener((obs, o, n) -> updateSelectedInfo());

        // Bind player data
        Player player = gameEngine.getCurrentPlayer();
        if (player != null) {
            elixirLabel.textProperty().bind(player.elixirProperty().asString("Elixir: %d"));
            updateArmyDisplay();
            updateArmySizeLabel();

            player.getArmy().addListener((javafx.collections.ListChangeListener<Unit>) c -> {
                Platform.runLater(() -> {
                    updateArmyDisplay();
                    updateArmySizeLabel();
                });
            });
        }

        // Build troop cards
        buildTroopCards();

        // Listen for training result from server
        gameEngine.lastTrainResultProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isEmpty()) return;
            Platform.runLater(() -> {
                trainButton.setDisable(false);
                if (newVal.startsWith("SUCCESS:")) {
                    statusLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 11;");
                    statusLabel.setText(newVal.substring(8));
                } else if (newVal.startsWith("FAIL:")) {
                    statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11;");
                    statusLabel.setText(newVal.substring(5));
                }
            });
        });

        // Refresh cards when base updates (barracks level might change)
        gameEngine.baseUpdateCounterProperty().addListener((obs, o, n) -> {
            Platform.runLater(() -> {
                buildTroopCards();
                if (selectedTroop != null) selectTroop(selectedTroop);
            });
        });

        statusLabel.setText("");

        // Training queue
        trainingQueueListView.setItems(gameEngine.getTrainingQueue());
        gameEngine.getTrainingQueue().addListener((javafx.collections.ListChangeListener<String>) c -> {
            Platform.runLater(() -> speedUpTrainingButton.setDisable(gameEngine.getTrainingQueue().isEmpty()));
        });
        speedUpTrainingButton.setDisable(gameEngine.getTrainingQueue().isEmpty());
    }

    /**
     * Build the troop selection card grid - same style as Laboratory.
     */
    private void buildTroopCards() {
        troopCardsPane.getChildren().clear();
        int barracksLevel = getMaxBarracksLevel();

        for (String troopType : TROOP_ORDER) {
            VBox card = createTroopCard(troopType, barracksLevel);
            troopCardsPane.getChildren().add(card);
        }
    }

    /**
     * Create a single troop card (matching Laboratory style).
     */
    private VBox createTroopCard(String troopType, int barracksLevel) {
        int reqLevel = REQUIRED_BARRACKS_LEVEL.getOrDefault(troopType, 1);
        boolean isLocked = barracksLevel < reqLevel;
        String accentColor = TROOP_COLORS.getOrDefault(troopType, "#607d8b");
        String emoji = TROOP_EMOJIS.getOrDefault(troopType, "⚔");

        VBox card = new VBox(4);
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(110);
        card.setPrefHeight(130);
        card.setPadding(new Insets(8, 6, 8, 6));
        card.setCursor(Cursor.HAND);

        String bgColor = isLocked ? "rgba(80,80,80,0.5)" : "rgba(0,40,70,0.85)";
        String borderColor = isLocked ? "#607d8b" : accentColor;

        card.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 2; "
                + "-fx-border-radius: 10; -fx-background-radius: 10;", bgColor, borderColor));

        // Emoji
        Label emojiLabel = new Label(isLocked ? "🔒" : emoji);
        emojiLabel.setStyle("-fx-font-size: 32; -fx-effect: dropshadow(gaussian, " + accentColor + ", 6, 0.3, 0, 0);");

        // Name
        Label nameLabel = new Label(troopType.charAt(0) + troopType.substring(1).toLowerCase());
        nameLabel.setStyle("-fx-text-fill: " + (isLocked ? "#78909c" : "white") + "; -fx-font-size: 11; -fx-font-weight: bold;");

        // Cost or Lock info
        Label infoLabel;
        if (isLocked) {
            infoLabel = new Label("Barracks Lv" + reqLevel);
            infoLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 10;");
        } else {
            int cost = UNIT_COSTS.getOrDefault(troopType, 0);
            infoLabel = new Label("💧 " + cost);
            infoLabel.setStyle("-fx-text-fill: #ce93d8; -fx-font-size: 11; -fx-font-weight: bold;");
        }

        card.getChildren().addAll(emojiLabel, nameLabel, infoLabel);

        // Hover effect
        card.setOnMouseEntered(e -> {
            if (card != selectedCard) {
                card.setStyle(String.format(
                        "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 2.5; "
                        + "-fx-border-radius: 10; -fx-background-radius: 10; "
                        + "-fx-effect: dropshadow(gaussian, %s, 14, 0.4, 0, 0);",
                        bgColor, borderColor, accentColor));
            }
        });
        card.setOnMouseExited(e -> {
            if (card != selectedCard) {
                card.setStyle(String.format(
                        "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 2; "
                        + "-fx-border-radius: 10; -fx-background-radius: 10;",
                        bgColor, borderColor));
            }
        });

        // Click
        card.setOnMouseClicked(e -> {
            if (isLocked) {
                statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11;");
                statusLabel.setText("🔒 Locked! Upgrade Barracks to Lv" + reqLevel);
                return;
            }
            // Deselect previous
            if (selectedCard != null) {
                String prevType = (String) selectedCard.getUserData();
                String prevAccent = TROOP_COLORS.getOrDefault(prevType, "#607d8b");
                selectedCard.setStyle(String.format(
                        "-fx-background-color: rgba(0,40,70,0.85); -fx-border-color: %s; -fx-border-width: 2; "
                        + "-fx-border-radius: 10; -fx-background-radius: 10;", prevAccent));
            }
            // Select new
            selectedCard = card;
            card.setStyle("-fx-background-color: rgba(255,215,0,0.12); -fx-border-color: #FFD700; -fx-border-width: 3; "
                    + "-fx-border-radius: 10; -fx-background-radius: 10; "
                    + "-fx-effect: dropshadow(gaussian, rgba(255,215,0,0.6), 16, 0.5, 0, 0);");
            selectTroop(troopType);
        });

        card.setUserData(troopType);
        return card;
    }

    /**
     * Show detail panel for the selected troop.
     */
    private void selectTroop(String troopType) {
        selectedTroop = troopType;
        String emoji = TROOP_EMOJIS.getOrDefault(troopType, "⚔");
        String displayName = troopType.charAt(0) + troopType.substring(1).toLowerCase();
        String role = TROOP_ROLES.getOrDefault(troopType, "Fighter");

        troopDetailCard.setVisible(true);
        troopDetailCard.setManaged(true);
        trainPanel.setVisible(true);
        trainPanel.setManaged(true);

        selectedTroopEmoji.setText(emoji);
        selectedTroopName.setText(displayName);
        unitInfoLabel.setText(role);

        updateSelectedInfo();
        statusLabel.setText("");
    }

    /**
     * Update stats and cost info for the currently selected troop.
     */
    private void updateSelectedInfo() {
        if (selectedTroop == null) return;
        int cost = UNIT_COSTS.getOrDefault(selectedTroop, 0);
        int housing = UNIT_HOUSING.getOrDefault(selectedTroop, 1);
        int hp = UNIT_HP.getOrDefault(selectedTroop, 0);
        int atk = UNIT_ATK.getOrDefault(selectedTroop, 0);
        int spd = UNIT_SPEED.getOrDefault(selectedTroop, 5);
        int count = countSpinner.getValue();
        int totalCost = cost * count;
        int totalHousing = housing * count;
        statsInfoLabel.setText(String.format(
                "❤ HP: %d  |  ⚔ ATK: %d  |  💨 SPD: %d\n💧 Cost: %d × %d = %d  |  🏠 Housing: %d",
                hp, atk, spd, cost, count, totalCost, totalHousing));
    }

    // ─── Event Handlers ──────────────────────────────────────────────

    @FXML
    private void onTrainClicked() {
        if (selectedTroop == null) {
            statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11;");
            statusLabel.setText("Select a troop first!");
            return;
        }

        int count = countSpinner.getValue();

        Player player = gameEngine.getCurrentPlayer();
        if (player != null) {
            int costPerUnit = UNIT_COSTS.getOrDefault(selectedTroop, 0);
            int totalCost = costPerUnit * count;
            if (player.getElixir() < totalCost) {
                statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11;");
                statusLabel.setText("Not enough elixir! Need " + totalCost + ", have " + player.getElixir());
                return;
            }

            int currentSize = player.getArmySize();
            int housing = UNIT_HOUSING.getOrDefault(selectedTroop, 1) * count;
            int maxCapacity = getMaxArmyCapacity();
            if (currentSize + housing > maxCapacity) {
                statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11;");
                statusLabel.setText("Not enough army camp space! (" + currentSize + "/" + maxCapacity + ")");
                return;
            }
        }

        trainButton.setDisable(true);
        statusLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-size: 11;");
        statusLabel.setText("Training " + count + "x " + selectedTroop + "...");
        gameEngine.trainUnits(selectedTroop, count);
    }

    @FXML
    private void onBackClicked() {
        gameEngine.navigateTo("dashboard");
    }

    @FXML
    private void onSpeedUpTrainingClicked() {
        gameEngine.speedUpTraining();
        statusLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-size: 12;");
        statusLabel.setText("Speeding up training...");
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private void updateArmyDisplay() {
        Player player = gameEngine.getCurrentPlayer();
        if (player == null) return;

        armyListView.getItems().clear();
        java.util.Map<String, Long> grouped = player.getArmy().stream()
                .collect(java.util.stream.Collectors.groupingBy(Unit::getType, java.util.stream.Collectors.counting()));
        grouped.forEach((type, count) ->
                armyListView.getItems().add(TROOP_EMOJIS.getOrDefault(type, "⚔") + " " + type + " x" + count));
    }

    private void updateArmySizeLabel() {
        Player player = gameEngine.getCurrentPlayer();
        if (player == null) return;
        int currentSize = player.getArmySize();
        int maxCapacity = getMaxArmyCapacity();
        armySizeLabel.setText("Army: " + currentSize + "/" + maxCapacity);
    }

    private int getMaxArmyCapacity() {
        Player player = gameEngine.getCurrentPlayer();
        if (player == null || player.getBase() == null) return 30;
        int total = player.getBase().getBuildings().stream()
                .filter(b -> "ARMY_CAMP".equals(b.getType()))
                .mapToInt(b -> b.getStorageCapacity() > 0 ? b.getStorageCapacity() : 30)
                .sum();
        return total > 0 ? total : 30;
    }

    private int getMaxBarracksLevel() {
        Player player = gameEngine.getCurrentPlayer();
        if (player == null || player.getBase() == null) return 0;
        return player.getBase().getBuildings().stream()
                .filter(b -> "BARRACKS".equals(b.getType()))
                .mapToInt(com.clashgame.client.model.Building::getLevel)
                .max().orElse(0);
    }
}
