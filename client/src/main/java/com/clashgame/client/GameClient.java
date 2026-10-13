package com.clashgame.client;

import com.clashgame.client.game.GameEngine;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Main entry point for the JavaFX game client.
 * 
 * Manages screen navigation between:
 * - Login Screen
 * - Dashboard (Main Menu)
 * - Base Building Screen
 * - Battle Screen
 * - Leaderboard Screen
 * 
 * Uses FXML files for layout and Controller classes for logic.
 */
public class GameClient extends Application {

    private Stage primaryStage;
    private final Map<String, Parent> screenCache = new HashMap<>();
    private final GameEngine gameEngine = GameEngine.getInstance();

    // ─── Application Lifecycle ───────────────────────────────────────

    @Override
    public void start(Stage stage) throws Exception {
        this.primaryStage = stage;

        // Configure screen navigator in the GameEngine
        gameEngine.setScreenNavigator(this::navigateTo);

        // Show login screen first
        navigateTo("login");

        primaryStage.setTitle("⚔ Clash of Clans Mini - Strategy Game");
        primaryStage.setResizable(false);
        primaryStage.setOnCloseRequest(event -> {
            gameEngine.logout();
            System.exit(0);
        });
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        gameEngine.logout();
        super.stop();
    }

    // ─── Screen Navigation ───────────────────────────────────────────

    /**
     * Navigate to the specified screen by name.
     * Loads FXML layout and switches the scene.
     * @param screenName one of: "login", "dashboard", "base", "battle", "leaderboard"
     */
    public void navigateTo(String screenName) {
        try {
            String fxmlFile = switch (screenName) {
                case "login"       -> "/fxml/login_screen.fxml";
                case "dashboard"   -> "/fxml/dashboard_screen.fxml";
                case "base"        -> "/fxml/base_screen.fxml";
                case "battle"      -> "/fxml/battle_screen.fxml";
                case "leaderboard" -> "/fxml/leaderboard_screen.fxml";
                case "train"       -> "/fxml/train_screen.fxml";
                case "history"     -> "/fxml/battle_history_screen.fxml";
                case "laboratory"  -> "/fxml/laboratory_screen.fxml";
                default -> throw new IllegalArgumentException("Unknown screen: " + screenName);
            };

            // Load fresh (don't cache screens that depend on dynamic state)
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFile));
            Parent root = loader.load();

            // Fade-in transition for smooth screen changes
            root.setOpacity(0);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), root);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);

            Scene scene = new Scene(root);

            // Apply stylesheet if available
            var cssUrl = getClass().getResource("/css/game_style.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }

            primaryStage.setScene(scene);
            fadeIn.play();

            // Update window title with player name if logged in
            if (gameEngine.getCurrentPlayer() != null && gameEngine.getCurrentPlayer().getUsername() != null) {
                primaryStage.setTitle("⚔ Clash of Clans Mini — " + gameEngine.getCurrentPlayer().getUsername());
            }

        } catch (IOException e) {
            System.err.println("Failed to load screen: " + screenName);
            e.printStackTrace();
        }
    }

    // ─── Main ────────────────────────────────────────────────────────

    public static void main(String[] args) {
        launch(args);
    }
}
