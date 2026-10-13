package com.clashgame.client.controller;

import com.clashgame.client.game.GameEngine;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

/**
 * Controller for the Battle History screen.
 * Displays the player's recent battle results fetched from the server.
 * 
 * FXML: battle_history_screen.fxml
 */
public class BattleHistoryController {

    @FXML private ListView<String> battleListView;
    @FXML private Label statusLabel;
    @FXML private Label summaryLabel;

    private final GameEngine gameEngine = GameEngine.getInstance();

    @FXML
    public void initialize() {
        // Bind list to the battle history data
        battleListView.setItems(gameEngine.getBattleHistory());

        // Update status when data arrives
        gameEngine.getBattleHistory().addListener((ListChangeListener<String>) c -> {
            Platform.runLater(() -> {
                int size = gameEngine.getBattleHistory().size();
                if (size > 0) {
                    statusLabel.setText("");
                    // Count wins/losses
                    long wins = gameEngine.getBattleHistory().stream()
                            .filter(s -> s.contains("⚔ ATK") || s.contains("★★★") || s.contains("★★") || s.contains("★"))
                            .filter(s -> s.contains("+"))
                            .count();
                    summaryLabel.setText("Showing " + size + " recent battles");
                } else {
                    statusLabel.setText("No battles found.");
                }
            });
        });

        // Request battle history from server
        gameEngine.requestBattleHistory();
    }

    @FXML
    private void onBackClicked() {
        gameEngine.navigateTo("dashboard");
    }
}
