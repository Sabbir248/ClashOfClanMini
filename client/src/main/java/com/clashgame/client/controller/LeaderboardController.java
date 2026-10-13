package com.clashgame.client.controller;

import com.clashgame.client.game.GameEngine;
import javafx.fxml.FXML;
import javafx.scene.control.*;

/**
 * Controller for the leaderboard screen.
 * Displays top players ranked by trophies, highlighting the current player.
 * 
 * FXML: leaderboard_screen.fxml
 */
public class LeaderboardController {

    @FXML private TableView<LeaderboardEntry> leaderboardTable;
    @FXML private TableColumn<LeaderboardEntry, Integer> rankColumn;
    @FXML private TableColumn<LeaderboardEntry, String> usernameColumn;
    @FXML private TableColumn<LeaderboardEntry, Integer> levelColumn;
    @FXML private TableColumn<LeaderboardEntry, Integer> trophiesColumn;
    @FXML private Button backButton;
    @FXML private Button refreshButton;

    private final GameEngine gameEngine = GameEngine.getInstance();

    @FXML
    public void initialize() {
        // Set up table columns
        rankColumn.setCellValueFactory(cd -> cd.getValue().rankProperty().asObject());
        usernameColumn.setCellValueFactory(cd -> cd.getValue().usernameProperty());
        levelColumn.setCellValueFactory(cd -> cd.getValue().levelProperty().asObject());
        trophiesColumn.setCellValueFactory(cd -> cd.getValue().trophiesProperty().asObject());

        // Highlight current player row
        String currentUsername = gameEngine.getCurrentPlayer() != null
                ? gameEngine.getCurrentPlayer().getUsername() : "";
        leaderboardTable.setRowFactory(tv -> new TableRow<LeaderboardEntry>() {
            @Override
            protected void updateItem(LeaderboardEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else if (item.usernameProperty().get().equals(currentUsername)) {
                    setStyle("-fx-background-color: rgba(255,215,0,0.25); -fx-font-weight: bold;");
                } else {
                    setStyle("");
                }
            }
        });

        // Bind table to GameEngine's leaderboard data (Bug 3 fix)
        leaderboardTable.setItems(gameEngine.getLeaderboardData());
        leaderboardTable.setPlaceholder(new Label("⏳ Loading leaderboard..."));

        // Request leaderboard data from server
        gameEngine.getNetworkClient().requestLeaderboard();
    }

    @FXML
    private void onBackClicked() {
        gameEngine.navigateTo("dashboard");
    }

    @FXML
    private void onRefreshClicked() {
        gameEngine.getNetworkClient().requestLeaderboard();
    }

    /**
     * Inner class representing a leaderboard table entry.
     */
    public static class LeaderboardEntry {
        private final javafx.beans.property.IntegerProperty rank;
        private final javafx.beans.property.StringProperty username;
        private final javafx.beans.property.IntegerProperty level;
        private final javafx.beans.property.IntegerProperty trophies;

        public LeaderboardEntry(int rank, String username, int level, int trophies) {
            this.rank = new javafx.beans.property.SimpleIntegerProperty(rank);
            this.username = new javafx.beans.property.SimpleStringProperty(username);
            this.level = new javafx.beans.property.SimpleIntegerProperty(level);
            this.trophies = new javafx.beans.property.SimpleIntegerProperty(trophies);
        }

        public javafx.beans.property.IntegerProperty rankProperty() { return rank; }
        public javafx.beans.property.StringProperty usernameProperty() { return username; }
        public javafx.beans.property.IntegerProperty levelProperty() { return level; }
        public javafx.beans.property.IntegerProperty trophiesProperty() { return trophies; }
    }
}
