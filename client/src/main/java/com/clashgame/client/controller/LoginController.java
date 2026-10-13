package com.clashgame.client.controller;

import com.clashgame.client.game.GameEngine;
import com.clashgame.client.util.UIUtils;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

/**
 * Controller for the Login/Registration screen.
 * Handles user authentication and new account creation.
 * 
 * FXML: login_screen.fxml
 */
public class LoginController {

    @FXML private TextField serverIpField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Button registerButton;
    @FXML private Label statusLabel;
    @FXML private ProgressIndicator loadingIndicator;

    private final GameEngine gameEngine = GameEngine.getInstance();

    // ─── Initialization ──────────────────────────────────────────────

    @FXML
    public void initialize() {
        loadingIndicator.setVisible(false);
        statusLabel.setText("");

        // Enter key triggers login from either text field
        usernameField.setOnAction(e -> onLoginClicked());
        passwordField.setOnAction(e -> onLoginClicked());

        // Network connection will be initialized on login/register with the entered IP

        // Listen for login/registration errors from the server
        gameEngine.clearLoginError();
        gameEngine.loginErrorProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                Platform.runLater(() -> {
                    statusLabel.setText(newVal);
                    loadingIndicator.setVisible(false);
                    loginButton.setDisable(false);
                    registerButton.setDisable(false);
                });
            }
        });
    }

    // ─── Event Handlers ──────────────────────────────────────────────

    @FXML
    private void onLoginClicked() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter username and password");
            return;
        }

        statusLabel.setText("");
        loadingIndicator.setVisible(true);
        loginButton.setDisable(true);
        registerButton.setDisable(true);
        statusLabel.setText("Connecting...");

        String serverIp = serverIpField.getText().trim();
        if (serverIp.isEmpty()) serverIp = "localhost";
        gameEngine.initialize(serverIp, 9090);

        // Run login on background thread to avoid freezing UI
        new Thread(() -> {
            try {
                gameEngine.login(username, password);
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("Connection failed: " + e.getMessage());
                    loadingIndicator.setVisible(false);
                    loginButton.setDisable(false);
                    registerButton.setDisable(false);
                });
            }
        }).start();
    }

    @FXML
    private void onRegisterClicked() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.length() < 4) {
            statusLabel.setText("Username required, password min 4 chars");
            return;
        }
        if (username.length() < 3 || username.length() > 20) {
            statusLabel.setText("Username must be 3-20 characters");
            return;
        }
        if (!username.matches("[a-zA-Z0-9_]+")) {
            statusLabel.setText("Username: only letters, numbers, underscore");
            return;
        }

        statusLabel.setText("");
        loadingIndicator.setVisible(true);
        loginButton.setDisable(true);
        registerButton.setDisable(true);
        statusLabel.setText("Registering...");

        String serverIp = serverIpField.getText().trim();
        if (serverIp.isEmpty()) serverIp = "localhost";
        gameEngine.initialize(serverIp, 9090);

        new Thread(() -> {
            try {
                gameEngine.register(username, password);
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("Registration failed: " + e.getMessage());
                    loadingIndicator.setVisible(false);
                    loginButton.setDisable(false);
                    registerButton.setDisable(false);
                });
            }
        }).start();
    }
}
