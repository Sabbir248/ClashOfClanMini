package com.clashgame.client.util;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Utility class for common UI operations.
 */
public final class UIUtils {

    private UIUtils() {} // prevent instantiation

    /**
     * Show an information alert dialog.
     */
    public static void showInfo(String title, String message) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Show an error alert dialog.
     */
    public static void showError(String title, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Show a warning alert dialog.
     */
    public static void showWarning(String title, String message) {
        Alert alert = new Alert(AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Draw a grid on a canvas (base grid background).
     */
    public static void drawGrid(Canvas canvas, int gridWidth, int gridHeight) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double cellW = canvas.getWidth() / gridWidth;
        double cellH = canvas.getHeight() / gridHeight;

        gc.setStroke(Color.rgb(100, 180, 80, 0.4)); // subtle green grid
        gc.setLineWidth(0.5);

        // Vertical lines
        for (int x = 0; x <= gridWidth; x++) {
            gc.strokeLine(x * cellW, 0, x * cellW, canvas.getHeight());
        }
        // Horizontal lines
        for (int y = 0; y <= gridHeight; y++) {
            gc.strokeLine(0, y * cellH, canvas.getWidth(), y * cellH);
        }
    }

    /**
     * Format a duration in seconds to MM:SS string.
     */
    public static String formatDuration(int seconds) {
        int mins = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    /**
     * Format large numbers with K/M suffixes.
     */
    public static String formatNumber(int number) {
        if (number >= 1_000_000) return String.format("%.1fM", number / 1_000_000.0);
        if (number >= 1_000) return String.format("%.1fK", number / 1_000.0);
        return String.valueOf(number);
    }
}
