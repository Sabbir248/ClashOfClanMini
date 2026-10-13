package com.clashgame.client.controller;

import com.clashgame.client.game.GameEngine;
import com.clashgame.client.model.Player;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.Map;

/**
 * Controller for the Laboratory screen.
 * Allows players to upgrade troops using the Laboratory building.
 * Troop selection uses beautiful card-based UI inspired by Clash of Clans.
 *
 * FXML: laboratory_screen.fxml
 */
public class LaboratoryController {

    @FXML private Label labLevelLabel;
    @FXML private Label elixirLabel;
    @FXML private HBox labStatusBar;
    @FXML private Label labStatusLabel;
    @FXML private ProgressBar labProgressBar;
    @FXML private Label labTimerLabel;
    @FXML private FlowPane troopCardsPane;

    // Detail panel
    @FXML private VBox troopDetailCard;
    @FXML private VBox statsPanel;
    @FXML private Label selectedTroopEmoji;
    @FXML private Label selectedTroopName;
    @FXML private Label selectedTroopLevel;
    @FXML private Label selectedTroopRole;
    @FXML private Label selectedTroopMaxLevel;

    // Stats comparison
    @FXML private Label currentHpLabel;
    @FXML private Label nextHpLabel;
    @FXML private Label currentAtkLabel;
    @FXML private Label nextAtkLabel;
    @FXML private Label currentSpdLabel;
    @FXML private Label nextSpdLabel;

    // Upgrade info
    @FXML private Label upgradeCostLabel;
    @FXML private Label upgradeTimeLabel;
    @FXML private Label reqLabLabel;
    @FXML private Button upgradeButton;
    @FXML private Label upgradeStatusLabel;

    private final GameEngine gameEngine = GameEngine.getInstance();
    private String selectedTroop = null;
    private VBox selectedCard = null;

    // Troop emojis for visual display
    private static final Map<String, String> TROOP_EMOJIS = Map.of(
            "BARBARIAN", "⚔",
            "ARCHER", "🏹",
            "GIANT", "🛡",
            "GOBLIN", "💰",
            "WIZARD", "✨",
            "DRAGON", "🐉",
            "HEALER", "❤"
    );

    // Troop display colors (border accent)
    private static final Map<String, String> TROOP_COLORS = Map.of(
            "BARBARIAN", "#ff9800",
            "ARCHER", "#e91e63",
            "GIANT", "#795548",
            "GOBLIN", "#4caf50",
            "WIZARD", "#9c27b0",
            "DRAGON", "#f44336",
            "HEALER", "#e91e63"
    );

    // Troop roles
    private static final Map<String, String> TROOP_ROLES = Map.of(
            "BARBARIAN", "⚔ Melee Attacker",
            "ARCHER", "🏹 Ranged Attacker",
            "GIANT", "🛡 Tank - Targets Defenses",
            "GOBLIN", "💰 Fast - Targets Resources",
            "WIZARD", "✨ Splash Damage",
            "DRAGON", "🐉 Flying Splash",
            "HEALER", "❤ Heals Friendly Units"
    );

    // Base stats from UnitType (must match server exactly)
    private static final Map<String, int[]> TROOP_BASE_STATS = Map.of(
            // {baseHP, baseATK, baseSPD, maxLevel, labReq, upgCostBase, upgTimeBase}
            "BARBARIAN", new int[]{50, 20, 8, 8, 1, 500, 30},
            "ARCHER",    new int[]{30, 15, 12, 8, 1, 600, 40},
            "GIANT",     new int[]{200, 35, 4, 7, 2, 1200, 60},
            "GOBLIN",    new int[]{25, 10, 14, 8, 1, 400, 25},
            "WIZARD",    new int[]{60, 50, 6, 6, 3, 2000, 90},
            "DRAGON",    new int[]{300, 80, 5, 5, 4, 4000, 120},
            "HEALER",    new int[]{80, 0, 6, 5, 3, 2500, 90}
    );

    // Ordered list for display
    private static final String[] TROOP_ORDER = {
            "BARBARIAN", "ARCHER", "GOBLIN", "GIANT", "WIZARD", "HEALER", "DRAGON"
    };

    @FXML
    public void initialize() {
        Player player = gameEngine.getCurrentPlayer();
        int labLevel = getLabLevel();

        // Update header
        labLevelLabel.setText("Lab Level: " + labLevel);
        if (player != null) {
            elixirLabel.textProperty().bind(player.elixirProperty().asString("Elixir: %d"));
        }

        // Build troop cards
        buildTroopCards();

        // Update lab status bar
        updateLabStatus();

        // Listen for lab upgrade result
        gameEngine.lastLabUpgradeResultProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isEmpty()) return;
            Platform.runLater(() -> {
                upgradeButton.setDisable(false);
                if (newVal.startsWith("SUCCESS:")) {
                    upgradeStatusLabel.setStyle("-fx-text-fill: #4caf50; -fx-font-size: 11;");
                    upgradeStatusLabel.setText(newVal.substring(8));
                    // Refresh everything
                    buildTroopCards();
                    updateLabStatus();
                    if (selectedTroop != null) selectTroop(selectedTroop);
                } else if (newVal.startsWith("FAIL:")) {
                    upgradeStatusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11;");
                    upgradeStatusLabel.setText(newVal.substring(5));
                }
            });
        });

        // Listen for base updates (troop levels may change)
        gameEngine.baseUpdateCounterProperty().addListener((obs, o, n) -> {
            Platform.runLater(() -> {
                labLevelLabel.setText("Lab Level: " + getLabLevel());
                buildTroopCards();
                updateLabStatus();
                if (selectedTroop != null) selectTroop(selectedTroop);
            });
        });
    }

    /**
     * Build the troop selection card grid - CoC style.
     */
    private void buildTroopCards() {
        troopCardsPane.getChildren().clear();

        for (String troopType : TROOP_ORDER) {
            VBox card = createTroopCard(troopType);
            troopCardsPane.getChildren().add(card);
        }
    }

    /**
     * Create a single troop selection card with CoC-style visuals.
     */
    private VBox createTroopCard(String troopType) {
        int currentLevel = getTroopLevel(troopType);
        int[] stats = TROOP_BASE_STATS.get(troopType);
        int maxLevel = stats[3];
        boolean isMaxed = currentLevel >= maxLevel;
        String accentColor = TROOP_COLORS.getOrDefault(troopType, "#607d8b");
        String emoji = TROOP_EMOJIS.getOrDefault(troopType, "⚔");

        // Check if currently upgrading this troop
        String upgradingTroop = gameEngine.labUpgradingTroopProperty().get();
        boolean isUpgrading = troopType.equals(upgradingTroop);

        VBox card = new VBox(4);
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(110);
        card.setPrefHeight(140);
        card.setPadding(new Insets(8, 6, 8, 6));
        card.setCursor(javafx.scene.Cursor.HAND);

        // Card styling
        String bgColor = isMaxed ? "rgba(76,175,80,0.15)" :
                          isUpgrading ? "rgba(255,171,64,0.2)" :
                          "rgba(0,40,70,0.85)";
        String borderColor = isMaxed ? "#4caf50" : isUpgrading ? "#ffab40" : accentColor;

        card.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 2; "
                + "-fx-border-radius: 10; -fx-background-radius: 10;", bgColor, borderColor));

        // Emoji icon
        Label emojiLabel = new Label(emoji);
        emojiLabel.setStyle("-fx-font-size: 32; -fx-effect: dropshadow(gaussian, " + accentColor + ", 6, 0.3, 0, 0);");

        // Troop name
        Label nameLabel = new Label(troopType.charAt(0) + troopType.substring(1).toLowerCase());
        nameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 11; -fx-font-weight: bold;");

        // Level badge
        String levelText = isMaxed ? "MAX" : "Lv " + currentLevel;
        String levelColor = isMaxed ? "#4caf50" : "#ffab40";
        Label levelLabel = new Label(levelText);
        levelLabel.setStyle("-fx-text-fill: " + levelColor + "; -fx-font-size: 12; -fx-font-weight: bold;");

        // Upgrading indicator
        if (isUpgrading) {
            Label upgLabel = new Label("⏱ Upgrading...");
            upgLabel.setStyle("-fx-text-fill: #ffab40; -fx-font-size: 9;");
            card.getChildren().addAll(emojiLabel, nameLabel, levelLabel, upgLabel);
        } else {
            card.getChildren().addAll(emojiLabel, nameLabel, levelLabel);
        }

        // Level stars (show filled stars for current level, max 5)
        int displayStars = Math.min(currentLevel, 5);
        StringBuilder starText = new StringBuilder();
        for (int i = 0; i < displayStars; i++) starText.append("★");
        for (int i = displayStars; i < Math.min(maxLevel, 5); i++) starText.append("☆");
        Label starsLabel = new Label(starText.toString());
        starsLabel.setStyle("-fx-text-fill: gold; -fx-font-size: 9;");
        card.getChildren().add(starsLabel);

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

        // Click: select troop
        card.setOnMouseClicked(e -> {
            // Deselect previous card
            if (selectedCard != null) {
                String prevType = (String) selectedCard.getUserData();
                int[] prevStats = TROOP_BASE_STATS.get(prevType);
                boolean prevMaxed = getTroopLevel(prevType) >= prevStats[3];
                String prevAccent = TROOP_COLORS.getOrDefault(prevType, "#607d8b");
                String prevBg = prevMaxed ? "rgba(76,175,80,0.15)" :
                                prevType.equals(upgradingTroop) ? "rgba(255,171,64,0.2)" :
                                "rgba(0,40,70,0.85)";
                String prevBorder = prevMaxed ? "#4caf50" : prevType.equals(upgradingTroop) ? "#ffab40" : prevAccent;
                selectedCard.setStyle(String.format(
                        "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 2; "
                        + "-fx-border-radius: 10; -fx-background-radius: 10;",
                        prevBg, prevBorder));
            }

            // Select new card
            selectedCard = card;
            card.setStyle(String.format(
                    "-fx-background-color: rgba(255,215,0,0.12); -fx-border-color: #FFD700; -fx-border-width: 3; "
                    + "-fx-border-radius: 10; -fx-background-radius: 10; "
                    + "-fx-effect: dropshadow(gaussian, rgba(255,215,0,0.6), 16, 0.5, 0, 0);"));
            selectTroop(troopType);
        });

        card.setUserData(troopType);
        return card;
    }

    /**
     * Display the selected troop's details and upgrade info.
     */
    private void selectTroop(String troopType) {
        selectedTroop = troopType;
        int currentLevel = getTroopLevel(troopType);
        int[] stats = TROOP_BASE_STATS.get(troopType);
        int baseHP = stats[0], baseATK = stats[1], baseSPD = stats[2];
        int maxLevel = stats[3], labReq = stats[4], upgCostBase = stats[5], upgTimeBase = stats[6];
        boolean isMaxed = currentLevel >= maxLevel;

        String emoji = TROOP_EMOJIS.getOrDefault(troopType, "⚔");
        String displayName = troopType.charAt(0) + troopType.substring(1).toLowerCase();
        String role = TROOP_ROLES.getOrDefault(troopType, "Fighter");

        // Show detail panels
        troopDetailCard.setVisible(true);
        troopDetailCard.setManaged(true);
        statsPanel.setVisible(true);
        statsPanel.setManaged(true);

        // Update troop card
        selectedTroopEmoji.setText(emoji);
        selectedTroopName.setText(displayName);
        selectedTroopLevel.setText("Level " + currentLevel + (isMaxed ? " (MAX)" : ""));
        selectedTroopLevel.setStyle(isMaxed
                ? "-fx-text-fill: #4caf50; -fx-font-size: 14; -fx-font-weight: bold;"
                : "-fx-text-fill: #80deea; -fx-font-size: 14;");
        selectedTroopRole.setText(role);
        selectedTroopMaxLevel.setText("Max Lv: " + maxLevel);

        // Current stats
        int curHP = calcStat(baseHP, currentLevel, 0.10);
        int curATK = calcStat(baseATK, currentLevel, 0.10);
        int curSPD = calcStat(baseSPD, currentLevel, 0.02);
        currentHpLabel.setText(String.valueOf(curHP));
        currentAtkLabel.setText(String.valueOf(curATK));
        currentSpdLabel.setText(String.valueOf(curSPD));

        if (isMaxed) {
            // At max level
            nextHpLabel.setText("MAX");
            nextAtkLabel.setText("MAX");
            nextSpdLabel.setText("MAX");
            nextHpLabel.setStyle("-fx-text-fill: #4caf50; -fx-font-size: 13; -fx-font-weight: bold;");
            nextAtkLabel.setStyle("-fx-text-fill: #4caf50; -fx-font-size: 13; -fx-font-weight: bold;");
            nextSpdLabel.setStyle("-fx-text-fill: #4caf50; -fx-font-size: 13; -fx-font-weight: bold;");
            upgradeCostLabel.setText("—");
            upgradeTimeLabel.setText("—");
            reqLabLabel.setText("—");
            upgradeButton.setText("✅ Max Level");
            upgradeButton.setDisable(true);
            upgradeButton.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white; "
                    + "-fx-font-size: 15; -fx-font-weight: bold; -fx-background-radius: 8;");
            upgradeStatusLabel.setText("");
        } else {
            int targetLevel = currentLevel + 1;
            int nextHP = calcStat(baseHP, targetLevel, 0.10);
            int nextATK = calcStat(baseATK, targetLevel, 0.10);
            int nextSPD = calcStat(baseSPD, targetLevel, 0.02);

            // Next level stats with green highlight
            nextHpLabel.setText(String.valueOf(nextHP));
            nextAtkLabel.setText(String.valueOf(nextATK));
            nextSpdLabel.setText(String.valueOf(nextSPD));
            nextHpLabel.setStyle("-fx-text-fill: #4caf50; -fx-font-size: 13; -fx-font-weight: bold;");
            nextAtkLabel.setStyle("-fx-text-fill: #4caf50; -fx-font-size: 13; -fx-font-weight: bold;");
            nextSpdLabel.setStyle("-fx-text-fill: #4caf50; -fx-font-size: 13; -fx-font-weight: bold;");

            // Upgrade cost and time
            int cost = upgCostBase * targetLevel;
            int time = upgTimeBase * targetLevel;
            int reqLab = labReq + (targetLevel - 2);

            upgradeCostLabel.setText("💧 " + cost);
            upgradeTimeLabel.setText("⏱ " + formatTime(time));
            reqLabLabel.setText("🧪 Lv" + reqLab);

            int labLevel = getLabLevel();
            boolean canAfford = (gameEngine.getCurrentPlayer() != null &&
                    gameEngine.getCurrentPlayer().getElixir() >= cost);
            boolean labMeetsReq = labLevel >= reqLab;
            String upgradingTroop = gameEngine.labUpgradingTroopProperty().get();
            boolean labBusy = upgradingTroop != null;

            // Color the req label based on whether met
            reqLabLabel.setStyle(labMeetsReq
                    ? "-fx-text-fill: #4caf50; -fx-font-size: 14; -fx-font-weight: bold;"
                    : "-fx-text-fill: #e74c3c; -fx-font-size: 14; -fx-font-weight: bold;");
            upgradeCostLabel.setStyle(canAfford
                    ? "-fx-text-fill: #ce93d8; -fx-font-size: 14; -fx-font-weight: bold;"
                    : "-fx-text-fill: #e74c3c; -fx-font-size: 14; -fx-font-weight: bold;");

            upgradeButton.setText("⬆ Upgrade to Lv " + targetLevel);
            upgradeButton.setDisable(labBusy || !canAfford || !labMeetsReq);

            if (labBusy) {
                upgradeButton.setStyle("-fx-background-color: #455a64; -fx-text-fill: #90a4ae; "
                        + "-fx-font-size: 15; -fx-font-weight: bold; -fx-background-radius: 8;");
            } else {
                upgradeButton.setStyle("-fx-background-color: linear-gradient(to bottom, #00bcd4, #00838f); "
                        + "-fx-text-fill: white; -fx-font-size: 15; -fx-font-weight: bold; -fx-background-radius: 8;");
            }

            // Status hints
            if (!labMeetsReq) {
                upgradeStatusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11;");
                upgradeStatusLabel.setText("Requires Laboratory Level " + reqLab + " (yours: " + labLevel + ")");
            } else if (!canAfford) {
                upgradeStatusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11;");
                upgradeStatusLabel.setText("Not enough elixir!");
            } else if (labBusy) {
                upgradeStatusLabel.setStyle("-fx-text-fill: #ffab40; -fx-font-size: 11;");
                upgradeStatusLabel.setText("Laboratory is busy upgrading " + upgradingTroop);
            } else {
                upgradeStatusLabel.setText("");
            }
        }
    }

    /**
     * Update the lab status bar (showing current upgrade progress).
     */
    private void updateLabStatus() {
        String upgradingTroop = gameEngine.labUpgradingTroopProperty().get();
        int timeRemaining = gameEngine.labUpgradeTimeRemainingProperty().get();

        if (upgradingTroop != null) {
            String displayName = upgradingTroop.charAt(0) + upgradingTroop.substring(1).toLowerCase();
            labStatusLabel.setText("⏱ Upgrading " + displayName + "...");
            labStatusLabel.setStyle("-fx-text-fill: #ffab40; -fx-font-size: 13;");
            labTimerLabel.setText(formatTime(timeRemaining) + " remaining");
            labProgressBar.setVisible(true);
            // Estimate progress (we don't know exact total, but can show time)
            labProgressBar.setProgress(timeRemaining > 0 ? -1.0 : 1.0); // indeterminate while active
            labStatusBar.setStyle("-fx-background-color: rgba(40,30,0,0.9); -fx-padding: 8 20; "
                    + "-fx-border-color: #ffab40; -fx-border-width: 0 0 1 0;");
        } else {
            labStatusLabel.setText("Laboratory is idle — select a troop to upgrade!");
            labStatusLabel.setStyle("-fx-text-fill: #80deea; -fx-font-size: 13;");
            labTimerLabel.setText("");
            labProgressBar.setVisible(false);
            labStatusBar.setStyle("-fx-background-color: rgba(0,30,60,0.9); -fx-padding: 8 20; "
                    + "-fx-border-color: #00838f; -fx-border-width: 0 0 1 0;");
        }
    }

    // ─── Event Handlers ──────────────────────────────────────────────

    @FXML
    private void onUpgradeClicked() {
        if (selectedTroop == null) {
            upgradeStatusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11;");
            upgradeStatusLabel.setText("Select a troop first!");
            return;
        }

        upgradeButton.setDisable(true);
        upgradeStatusLabel.setStyle("-fx-text-fill: #ffab40; -fx-font-size: 11;");
        upgradeStatusLabel.setText("Starting upgrade...");
        gameEngine.upgradeUnit(selectedTroop);
    }

    @FXML
    private void onBackClicked() {
        gameEngine.navigateTo("dashboard");
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private int getLabLevel() {
        Player player = gameEngine.getCurrentPlayer();
        if (player == null || player.getBase() == null) return 0;
        return player.getBase().getBuildings().stream()
                .filter(b -> "LABORATORY".equals(b.getType()))
                .mapToInt(com.clashgame.client.model.Building::getLevel)
                .max().orElse(0);
    }

    private int getTroopLevel(String troopType) {
        Integer level = gameEngine.getTroopLevels().get(troopType);
        return level != null ? level : 1;
    }

    /**
     * Calculate a stat at a given level: base * (1 + (level-1) * multiplier)
     */
    private int calcStat(int base, int level, double multiplier) {
        return (int)(base * (1.0 + (level - 1) * multiplier));
    }

    /**
     * Format seconds into a readable time string.
     */
    private String formatTime(int seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }
}
