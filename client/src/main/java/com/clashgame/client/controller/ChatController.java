package com.clashgame.client.controller;

import com.clashgame.client.game.GameEngine;
import javafx.fxml.FXML;
import javafx.scene.control.*;

/**
 * Controller for the chat panel (embedded or standalone).
 * Supports global chat between all online players.
 * 
 * FXML: chat_panel.fxml
 */
public class ChatController {

    @FXML private ListView<String> chatListView;
    @FXML private TextField chatInput;
    @FXML private Button sendButton;

    private final GameEngine gameEngine = GameEngine.getInstance();

    @FXML
    public void initialize() {
        // Bind chat list to game engine's chat messages
        chatListView.setItems(gameEngine.getChatMessages());

        // Enforce 200 character limit on chat input
        chatInput.setTextFormatter(new javafx.scene.control.TextFormatter<>(change -> {
            if (change.getControlNewText().length() > 200) return null;
            return change;
        }));

        // Auto-scroll to bottom when new messages arrive
        gameEngine.getChatMessages().addListener(
                (javafx.collections.ListChangeListener<String>) change -> {
                    while (change.next()) {
                        if (change.wasAdded()) {
                            chatListView.scrollTo(chatListView.getItems().size() - 1);
                        }
                    }
                });
    }

    @FXML
    private void onSendClicked() {
        String message = chatInput.getText().trim();
        if (!message.isEmpty()) {
            gameEngine.sendChatMessage(message);
            chatInput.clear();
        }
    }

    @FXML
    private void onChatInputAction() {
        // Enter key handler (same as send button)
        onSendClicked();
    }
}
