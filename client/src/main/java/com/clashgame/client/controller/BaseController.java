package com.clashgame.client.controller;

import com.clashgame.client.game.GameEngine;
import com.clashgame.client.model.Base;
import com.clashgame.client.model.Building;
import com.clashgame.client.util.UIUtils;
import com.clashgame.server.model.GameMessage;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Controller for the base building/editing screen.
 * Renders the base grid on a Canvas and handles building placement.
 * 
 * Features:
 * - Grid-based building placement with drag and drop
 * - Building info panel showing stats
 * - Upgrade and remove building actions
 * - Visual building type selector
 * - Auto-refreshes when server sends updated base data
 * 
 * FXML: base_screen.fxml
 */
public class BaseController {

    @FXML private Canvas baseCanvas;
    @FXML private ComboBox<String> buildingTypeCombo;
    @FXML private Label buildingInfoLabel;
    @FXML private Button placeButton;
    @FXML private Button upgradeButton;
    @FXML private Button removeButton;
    @FXML private Button moveButton;
    @FXML private Button speedUpButton;
    @FXML private Button backButton;
    @FXML private Label goldLabel;
    @FXML private Label elixirLabel;

    private final GameEngine gameEngine = GameEngine.getInstance();
    private Building selectedBuilding;

    // Grid rendering
    private double cellWidth;
    private double cellHeight;

    // Placement mode
    private boolean placementMode = false;
    private String selectedBuildingType;

    // Move mode
    private boolean moveMode = false;
    private Building movingBuilding = null;

    // Listener for base update counter (for cleanup)
    private ChangeListener<Number> baseUpdateListener;

    // ─── Initialization ──────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Populate building type selector with costs
        java.util.Map<String, Integer> BUILDING_COSTS = java.util.Map.ofEntries(
                java.util.Map.entry("GOLD_MINE", 150), java.util.Map.entry("ELIXIR_COLLECTOR", 150),
                java.util.Map.entry("GOLD_STORAGE", 300), java.util.Map.entry("ELIXIR_STORAGE", 300),
                java.util.Map.entry("CANNON", 250), java.util.Map.entry("ARCHER_TOWER", 500),
                java.util.Map.entry("MORTAR", 800), java.util.Map.entry("WALL", 50),
                java.util.Map.entry("BARRACKS", 200), java.util.Map.entry("ARMY_CAMP", 250),
                java.util.Map.entry("LABORATORY", 600)
        );
        buildingTypeCombo.getItems().addAll(
                "GOLD_MINE", "ELIXIR_COLLECTOR", "GOLD_STORAGE", "ELIXIR_STORAGE",
                "CANNON", "ARCHER_TOWER", "MORTAR", "WALL",
                "BARRACKS", "ARMY_CAMP", "LABORATORY"
        );
        // Show cost info when selection changes
        buildingTypeCombo.setOnAction(e -> {
            String type = buildingTypeCombo.getValue();
            if (type != null && selectedBuilding == null) {
                int cost = BUILDING_COSTS.getOrDefault(type, 0);
                buildingInfoLabel.setText(type.replace("_", " ") + "\nCost: " + cost + " gold");
            }
        });

        // Calculate cell dimensions
        cellWidth = baseCanvas.getWidth() / 20.0;
        cellHeight = baseCanvas.getHeight() / 20.0;

        // Canvas click handler
        baseCanvas.setOnMouseClicked(this::onCanvasClicked);

        // Bind resources
        if (gameEngine.getCurrentPlayer() != null) {
            goldLabel.textProperty().bind(gameEngine.getCurrentPlayer().goldProperty().asString("Gold: %d"));
            elixirLabel.textProperty().bind(gameEngine.getCurrentPlayer().elixirProperty().asString("Elixir: %d"));
        }

        // Listen for base updates from server → auto re-render
        baseUpdateListener = (obs, oldVal, newVal) -> {
            selectedBuilding = null; // clear stale selection
            renderBase();
        };
        gameEngine.baseUpdateCounterProperty().addListener(baseUpdateListener);

        // Initial render
        renderBase();
    }

    // ─── Rendering ───────────────────────────────────────────────────

    /**
     * Always read the latest base from the player (never cache a stale reference).
     */
    private Base getCurrentBase() {
        if (gameEngine.getCurrentPlayer() != null) {
            return gameEngine.getCurrentPlayer().getBase();
        }
        return null;
    }

    /**
     * Render the entire base grid and all buildings.
     */
    private void renderBase() {
        GraphicsContext gc = baseCanvas.getGraphicsContext2D();
        double w = baseCanvas.getWidth();
        double h = baseCanvas.getHeight();

        // Clear canvas
        gc.setFill(Color.rgb(76, 153, 0)); // grass green
        gc.fillRect(0, 0, w, h);

        // Draw grid lines
        UIUtils.drawGrid(baseCanvas, 20, 20);

        // Draw buildings (always read fresh from player)
        Base base = getCurrentBase();
        if (base != null) {
            for (Building building : base.getBuildings()) {
                drawBuilding(gc, building);
            }
        }

        // Highlight selected building
        if (selectedBuilding != null) {
            gc.setStroke(Color.YELLOW);
            gc.setLineWidth(3);
            gc.strokeRect(
                    selectedBuilding.getGridX() * cellWidth,
                    selectedBuilding.getGridY() * cellHeight,
                    selectedBuilding.getWidth() * cellWidth,
                    selectedBuilding.getHeight() * cellHeight
            );
        }
    }

    /**
     * Draw a single building on the canvas with detailed visuals for defense buildings.
     */
    private void drawBuilding(GraphicsContext gc, Building building) {
        double x = building.getGridX() * cellWidth;
        double y = building.getGridY() * cellHeight;
        double bw = building.getWidth() * cellWidth;
        double bh = building.getHeight() * cellHeight;

        String type = building.getType();
        int level = building.getLevel();
        switch (type) {
            case "CANNON"           -> drawCannon(gc, x, y, bw, bh, level);
            case "ARCHER_TOWER"     -> drawArcherTower(gc, x, y, bw, bh, level);
            case "MORTAR"           -> drawMortar(gc, x, y, bw, bh, level);
            case "WALL"             -> drawWall(gc, x, y, bw, bh, level);
            case "TOWN_HALL"        -> drawTownHall(gc, x, y, bw, bh, level);
            case "GOLD_MINE"        -> drawGoldMine(gc, x, y, bw, bh, level);
            case "ELIXIR_COLLECTOR" -> drawElixirCollector(gc, x, y, bw, bh, level);
            case "GOLD_STORAGE"     -> drawGoldStorage(gc, x, y, bw, bh, level);
            case "ELIXIR_STORAGE"   -> drawElixirStorage(gc, x, y, bw, bh, level);
            case "BARRACKS"         -> drawBarracks(gc, x, y, bw, bh, level);
            case "ARMY_CAMP"        -> drawArmyCamp(gc, x, y, bw, bh, level);
            case "LABORATORY"       -> drawLaboratory(gc, x, y, bw, bh, level);
            default                 -> drawGenericBuilding(gc, building, x, y, bw, bh);
        }

        // Draw health bar if damaged (all buildings)
        if (building.getCurrentHitPoints() < building.getHitPoints()) {
            double healthPct = (double) building.getCurrentHitPoints() / building.getHitPoints();
            gc.setFill(Color.rgb(60, 0, 0));
            gc.fillRect(x + 1, y + bh - 5, bw - 2, 4);
            Color barColor = healthPct > 0.5 ? Color.LIME : healthPct > 0.25 ? Color.ORANGE : Color.RED;
            gc.setFill(barColor);
            gc.fillRect(x + 1, y + bh - 5, (bw - 2) * healthPct, 4);
        }

        // Draw upgrading indicator (hammer icon)
        if (building.isUpgrading()) {
            gc.setFill(Color.rgb(255, 200, 0, 0.85));
            gc.fillRoundRect(x + bw - 16, y + 1, 15, 14, 4, 4);
            gc.setFill(Color.rgb(80, 50, 20));
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 10));
            gc.fillText("\u2692", x + bw - 14, y + 12); // hammer symbol
        }
    }

    /** Draw a non-defense building with the standard style. */
    private void drawGenericBuilding(GraphicsContext gc, Building building, double x, double y, double bw, double bh) {
        gc.setFill(building.getColor());
        gc.fillRect(x + 1, y + 1, bw - 2, bh - 2);
        gc.setStroke(Color.rgb(30, 30, 30));
        gc.setLineWidth(1);
        gc.strokeRect(x + 1, y + 1, bw - 2, bh - 2);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        gc.fillText(building.getDisplayLabel(), x + bw / 2 - 6, y + bh / 2 + 4);
        gc.setFill(Color.YELLOW);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        gc.fillText("" + building.getLevel(), x + 3, y + 12);
    }

    // ─── Defense Building Detailed Renderers ─────────────────────────

    /**
     * Draw a Cannon: stone platform with a dark barrel pointing outward.
     */
    private void drawCannon(GraphicsContext gc, double x, double y, double bw, double bh, int level) {
        double cx = x + bw / 2, cy = y + bh / 2;
        double pad = 2;

        // Stone platform (rounded rect feel via two layers)
        gc.setFill(Color.rgb(140, 130, 115));
        gc.fillRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 8, 8);
        gc.setFill(Color.rgb(160, 150, 135));
        gc.fillRoundRect(x + pad + 2, y + pad + 2, bw - pad * 2 - 4, bh - pad * 2 - 4, 6, 6);

        // Barrel base (dark circle)
        double baseR = Math.min(bw, bh) * 0.32;
        gc.setFill(Color.rgb(60, 55, 50));
        gc.fillOval(cx - baseR, cy - baseR, baseR * 2, baseR * 2);

        // Barrel (dark rectangle pointing right)
        double barrelW = bw * 0.45, barrelH = bh * 0.18;
        gc.setFill(Color.rgb(45, 42, 38));
        gc.fillRoundRect(cx - 2, cy - barrelH / 2, barrelW, barrelH, 4, 4);

        // Barrel highlight
        gc.setFill(Color.rgb(80, 75, 68));
        gc.fillRoundRect(cx, cy - barrelH / 2 + 1, barrelW - 4, barrelH / 2 - 1, 3, 3);

        // Barrel muzzle ring
        gc.setStroke(Color.rgb(90, 85, 75));
        gc.setLineWidth(1.5);
        gc.strokeOval(cx + barrelW - 5, cy - barrelH / 2 - 1, 5, barrelH + 2);

        // Center rivet
        gc.setFill(Color.rgb(100, 95, 88));
        gc.fillOval(cx - 3, cy - 3, 6, 6);

        // Level badge (bottom-right)
        drawLevelBadge(gc, x + bw - 13, y + bh - 13, level, Color.rgb(80, 80, 80));

        // Border
        gc.setStroke(Color.rgb(90, 80, 70));
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 8, 8);
    }

    /**
     * Draw an Archer Tower: tall brown stone tower with a pointed turret top.
     */
    private void drawArcherTower(GraphicsContext gc, double x, double y, double bw, double bh, int level) {
        double cx = x + bw / 2, cy = y + bh / 2;
        double pad = 2;

        // Tower base (dark brown stone)
        gc.setFill(Color.rgb(110, 70, 35));
        gc.fillRoundRect(x + pad + 3, y + bh * 0.3, bw - pad * 2 - 6, bh * 0.68 - pad, 4, 4);

        // Tower body (lighter brown)
        gc.setFill(Color.rgb(140, 95, 50));
        gc.fillRoundRect(x + pad + 5, y + bh * 0.15, bw - pad * 2 - 10, bh * 0.6, 3, 3);

        // Tower top platform (wider)
        gc.setFill(Color.rgb(120, 80, 40));
        gc.fillRect(x + pad + 2, y + bh * 0.12, bw - pad * 2 - 4, bh * 0.12);

        // Pointed turret roof (triangle)
        double roofL = x + pad;
        double roofR = x + bw - pad;
        double roofMid = cx;
        double roofTop = y + 1;
        double roofBase = y + bh * 0.15;
        gc.setFill(Color.rgb(160, 50, 50));  // reddish roof
        gc.fillPolygon(
                new double[]{roofL + 2, roofR - 2, roofMid},
                new double[]{roofBase, roofBase, roofTop},
                3);

        // Window slit
        gc.setFill(Color.rgb(40, 30, 20));
        gc.fillRect(cx - 2, cy + 2, 4, 6);

        // Crenellations (battlements) on the platform
        double crenY = y + bh * 0.11;
        double crenH = 3;
        for (double cw = x + pad + 3; cw < x + bw - pad - 3; cw += 6) {
            gc.setFill(Color.rgb(130, 85, 45));
            gc.fillRect(cw, crenY, 4, crenH);
        }

        // Bow/crossbow hint (small diagonal line at top)
        gc.setStroke(Color.rgb(90, 50, 25));
        gc.setLineWidth(2);
        gc.strokeLine(cx - 4, y + bh * 0.2, cx + 4, y + bh * 0.28);

        // Level badge
        drawLevelBadge(gc, x + bw - 13, y + bh - 13, level, Color.rgb(120, 60, 30));

        // Border
        gc.setStroke(Color.rgb(80, 50, 25));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x + pad + 3, y + bh * 0.3, bw - pad * 2 - 6, bh * 0.68 - pad, 4, 4);
    }

    /**
     * Draw a Mortar: heavy stone pit with a short wide barrel protruding upward.
     */
    private void drawMortar(GraphicsContext gc, double x, double y, double bw, double bh, int level) {
        double cx = x + bw / 2, cy = y + bh / 2;
        double pad = 2;

        // Stone platform (dark red-brown)
        gc.setFill(Color.rgb(100, 45, 30));
        gc.fillRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 10, 10);
        gc.setFill(Color.rgb(120, 55, 35));
        gc.fillRoundRect(x + pad + 2, y + pad + 2, bw - pad * 2 - 4, bh - pad * 2 - 4, 8, 8);

        // Mortar pit (dark circle inset)
        double pitR = Math.min(bw, bh) * 0.35;
        gc.setFill(Color.rgb(50, 25, 18));
        gc.fillOval(cx - pitR, cy - pitR, pitR * 2, pitR * 2);

        // Barrel (short, wide, angled upward — drawn as trapezoid)
        double barrelBotW = bw * 0.35;
        double barrelTopW = bw * 0.22;
        double barrelH = bh * 0.3;
        gc.setFill(Color.rgb(60, 35, 25));
        gc.fillPolygon(
                new double[]{cx - barrelBotW / 2, cx + barrelBotW / 2, cx + barrelTopW / 2, cx - barrelTopW / 2},
                new double[]{cy, cy, cy - barrelH, cy - barrelH},
                4);

        // Barrel muzzle (dark opening at top)
        gc.setFill(Color.rgb(25, 15, 10));
        gc.fillOval(cx - barrelTopW / 2, cy - barrelH - 2, barrelTopW, 6);

        // Mortar shell hint (small orange circle inside barrel)
        gc.setFill(Color.rgb(220, 120, 30, 0.8));
        gc.fillOval(cx - 3, cy - barrelH + 3, 6, 6);

        // Rivets on platform
        gc.setFill(Color.rgb(80, 45, 30));
        double rivetY = y + bh - pad - 6;
        gc.fillOval(x + pad + 5, rivetY, 4, 4);
        gc.fillOval(x + bw - pad - 9, rivetY, 4, 4);

        // Level badge
        drawLevelBadge(gc, x + bw - 13, y + bh - 13, level, Color.rgb(140, 50, 30));

        // Border
        gc.setStroke(Color.rgb(70, 35, 22));
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 10, 10);
    }

    /**
     * Draw a Wall segment: stone/brick pattern with mortar lines.
     */
    private void drawWall(GraphicsContext gc, double x, double y, double bw, double bh, int level) {
        // Wall base color depends on level
        Color wallColor;
        Color mortarColor;
        if (level >= 3) {
            wallColor = Color.rgb(120, 115, 130);   // stone-purple (high level)
            mortarColor = Color.rgb(90, 85, 100);
        } else if (level >= 2) {
            wallColor = Color.rgb(140, 135, 125);    // gray stone
            mortarColor = Color.rgb(105, 100, 90);
        } else {
            wallColor = Color.rgb(155, 145, 130);    // light sandstone
            mortarColor = Color.rgb(120, 112, 100);
        }

        // Fill base
        gc.setFill(wallColor);
        gc.fillRect(x, y, bw, bh);

        // Brick pattern — horizontal mortar lines
        gc.setStroke(mortarColor);
        gc.setLineWidth(0.8);
        double halfH = bh / 2;
        gc.strokeLine(x, y + halfH, x + bw, y + halfH);

        // Vertical mortar lines (offset for brick pattern)
        double halfW = bw / 2;
        gc.strokeLine(x + halfW, y, x + halfW, y + halfH);
        gc.strokeLine(x + halfW / 2, y + halfH, x + halfW / 2, y + bh);
        gc.strokeLine(x + halfW + halfW / 2, y + halfH, x + halfW + halfW / 2, y + bh);

        // Top highlight for 3D feel
        gc.setFill(Color.rgb(255, 255, 255, 0.15));
        gc.fillRect(x, y, bw, 2);

        // Bottom shadow
        gc.setFill(Color.rgb(0, 0, 0, 0.2));
        gc.fillRect(x, y + bh - 2, bw, 2);

        // Outline
        gc.setStroke(Color.rgb(80, 75, 65));
        gc.setLineWidth(1);
        gc.strokeRect(x, y, bw, bh);
    }

    // ─── Non-Defense Building Detailed Renderers ─────────────────────

    /**
     * Draw a Town Hall: grand medieval hall with turrets and golden roof.
     */
    private void drawTownHall(GraphicsContext gc, double x, double y, double bw, double bh, int level) {
        double cx = x + bw / 2;
        double pad = 2;

        // Stone foundation
        gc.setFill(Color.rgb(130, 120, 105));
        gc.fillRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);

        // Main building body (warm stone)
        gc.setFill(Color.rgb(195, 175, 135));
        gc.fillRect(x + bw * 0.15, y + bh * 0.35, bw * 0.7, bh * 0.6);

        // Side turrets
        double tW = bw * 0.14;
        gc.setFill(Color.rgb(160, 145, 115));
        gc.fillRect(x + pad + 2, y + bh * 0.25, tW, bh * 0.7);
        gc.fillRect(x + bw - pad - 2 - tW, y + bh * 0.25, tW, bh * 0.7);

        // Turret pointed caps (reddish)
        gc.setFill(Color.rgb(140, 50, 50));
        double tL = x + pad + 1, tR = tL + tW + 2, tMid = tL + (tW + 2) / 2;
        gc.fillPolygon(new double[]{tL, tR, tMid},
                       new double[]{y + bh * 0.27, y + bh * 0.27, y + bh * 0.15}, 3);
        tL = x + bw - pad - 3 - tW; tR = tL + tW + 2; tMid = tL + (tW + 2) / 2;
        gc.fillPolygon(new double[]{tL, tR, tMid},
                       new double[]{y + bh * 0.27, y + bh * 0.27, y + bh * 0.15}, 3);

        // Roof (golden-brown trapezoid)
        gc.setFill(Color.rgb(170, 110, 40));
        gc.fillPolygon(
            new double[]{x + bw * 0.12, x + bw * 0.88, x + bw * 0.78, x + bw * 0.22},
            new double[]{y + bh * 0.37, y + bh * 0.37, y + bh * 0.22, y + bh * 0.22}, 4);

        // Roof peak (triangle)
        gc.setFill(Color.rgb(190, 130, 50));
        gc.fillPolygon(
            new double[]{x + bw * 0.22, x + bw * 0.78, cx},
            new double[]{y + bh * 0.22, y + bh * 0.22, y + bh * 0.08}, 3);

        // Crenellations on main building top
        for (double cw = x + bw * 0.18; cw < x + bw * 0.82; cw += bw * 0.08) {
            gc.setFill(Color.rgb(175, 155, 120));
            gc.fillRect(cw, y + bh * 0.33, bw * 0.05, bh * 0.04);
        }

        // Door (central arch)
        gc.setFill(Color.rgb(80, 50, 25));
        double doorW = bw * 0.16;
        double doorH = bh * 0.25;
        gc.fillRect(cx - doorW / 2, y + bh - pad - doorH, doorW, doorH);
        gc.fillOval(cx - doorW / 2, y + bh - pad - doorH - doorW * 0.15, doorW, doorW * 0.3);

        // Windows (blue glass)
        gc.setFill(Color.rgb(150, 200, 255));
        double winW = bw * 0.08; double winH = bh * 0.1;
        gc.fillRect(cx - bw * 0.22, y + bh * 0.48, winW, winH);
        gc.fillRect(cx + bw * 0.14, y + bh * 0.48, winW, winH);

        // Gold emblem above door
        gc.setFill(Color.rgb(255, 215, 0));
        gc.fillOval(cx - 4, y + bh * 0.55, 8, 8);
        gc.setFill(Color.rgb(255, 240, 100));
        gc.fillOval(cx - 2, y + bh * 0.56, 4, 4);

        // Level badge
        drawLevelBadge(gc, x + bw - 14, y + bh - 14, level, Color.rgb(180, 140, 40));

        // Border
        gc.setStroke(Color.rgb(100, 90, 70));
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
    }

    /**
     * Draw a Gold Mine: wooden mine entrance with gold nuggets.
     */
    private void drawGoldMine(GraphicsContext gc, double x, double y, double bw, double bh, int level) {
        double cx = x + bw / 2;
        double pad = 2;

        // Earth/ground base
        gc.setFill(Color.rgb(120, 90, 50));
        gc.fillRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);

        // Dirt mound
        gc.setFill(Color.rgb(140, 105, 60));
        gc.fillOval(x + pad + 2, y + bh * 0.15, bw - pad * 2 - 4, bh * 0.7);

        // Mine tunnel opening (dark arch)
        double openW = bw * 0.5;
        double openH = bh * 0.4;
        gc.setFill(Color.rgb(30, 20, 10));
        gc.fillRect(cx - openW / 2, y + bh * 0.35, openW, openH);
        gc.fillOval(cx - openW / 2, y + bh * 0.28, openW, openH * 0.3);

        // Wooden frame supports
        gc.setFill(Color.rgb(160, 110, 50));
        gc.fillRect(cx - openW / 2 - 3, y + bh * 0.3, 4, openH + bh * 0.05);
        gc.fillRect(cx + openW / 2 - 1, y + bh * 0.3, 4, openH + bh * 0.05);
        gc.fillRect(cx - openW / 2 - 3, y + bh * 0.28, openW + 6, 4);

        // Gold nuggets
        gc.setFill(Color.rgb(255, 215, 0));
        gc.fillOval(x + bw * 0.15, y + bh * 0.7, 6, 5);
        gc.fillOval(x + bw * 0.62, y + bh * 0.72, 5, 4);
        gc.fillOval(cx - 1, y + bh * 0.78, 4, 4);

        // Pickaxe handle and head
        gc.setStroke(Color.rgb(130, 100, 60));
        gc.setLineWidth(1.5);
        gc.strokeLine(x + bw * 0.7, y + bh * 0.15, x + bw * 0.85, y + bh * 0.35);
        gc.setStroke(Color.rgb(160, 160, 170));
        gc.setLineWidth(1.2);
        gc.strokeLine(x + bw * 0.75, y + bh * 0.12, x + bw * 0.85, y + bh * 0.22);

        // Level badge
        drawLevelBadge(gc, x + bw - 13, y + bh - 13, level, Color.rgb(180, 150, 30));

        // Border
        gc.setStroke(Color.rgb(90, 70, 40));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
    }

    /**
     * Draw an Elixir Collector: apparatus with purple elixir container and pipes.
     */
    private void drawElixirCollector(GraphicsContext gc, double x, double y, double bw, double bh, int level) {
        double cx = x + bw / 2;
        double pad = 2;

        // Ground area
        gc.setFill(Color.rgb(100, 145, 60));
        gc.fillRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);

        // Wooden base platform
        gc.setFill(Color.rgb(140, 100, 55));
        gc.fillRoundRect(x + pad, y + bh * 0.7, bw - pad * 2, bh * 0.28, 4, 4);

        // Elixir container (rounded purple body)
        gc.setFill(Color.rgb(130, 60, 160));
        gc.fillRoundRect(cx - bw * 0.28, y + bh * 0.12, bw * 0.56, bh * 0.62, 8, 8);

        // Elixir liquid level (lighter purple)
        gc.setFill(Color.rgb(170, 80, 220));
        gc.fillRoundRect(cx - bw * 0.22, y + bh * 0.4, bw * 0.44, bh * 0.3, 6, 6);

        // Glass highlight
        gc.setFill(Color.rgb(180, 130, 210, 0.5));
        gc.fillRoundRect(cx - bw * 0.18, y + bh * 0.16, bw * 0.2, bh * 0.3, 4, 4);

        // Metal rim top and bottom
        gc.setFill(Color.rgb(120, 120, 130));
        gc.fillRect(cx - bw * 0.3, y + bh * 0.1, bw * 0.6, bh * 0.06);
        gc.fillRect(cx - bw * 0.3, y + bh * 0.7, bw * 0.6, bh * 0.06);

        // Pipe to side
        gc.setFill(Color.rgb(110, 110, 120));
        gc.fillRect(x + bw * 0.72, y + bh * 0.4, bw * 0.18, bh * 0.08);

        // Bubbles
        gc.setFill(Color.rgb(200, 150, 240, 0.6));
        gc.fillOval(cx - 2, y + bh * 0.3, 4, 4);
        gc.fillOval(cx + 5, y + bh * 0.24, 3, 3);

        // Level badge
        drawLevelBadge(gc, x + bw - 13, y + bh - 13, level, Color.rgb(120, 50, 160));

        // Border
        gc.setStroke(Color.rgb(80, 50, 100));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
    }

    /**
     * Draw Gold Storage: reinforced vault with gold piles visible.
     */
    private void drawGoldStorage(GraphicsContext gc, double x, double y, double bw, double bh, int level) {
        double cx = x + bw / 2;
        double pad = 2;

        // Base platform
        gc.setFill(Color.rgb(140, 120, 80));
        gc.fillRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);

        // Vault body (golden/orange)
        gc.setFill(Color.rgb(210, 160, 50));
        gc.fillRoundRect(x + pad + 3, y + bh * 0.2, bw - pad * 2 - 6, bh * 0.72, 8, 8);

        // Dome top
        gc.setFill(Color.rgb(230, 180, 60));
        gc.fillOval(x + bw * 0.15, y + bh * 0.08, bw * 0.7, bh * 0.35);

        // Metal reinforcement bands
        gc.setStroke(Color.rgb(140, 100, 30));
        gc.setLineWidth(2);
        gc.strokeLine(x + pad + 5, y + bh * 0.4, x + bw - pad - 5, y + bh * 0.4);
        gc.strokeLine(x + pad + 5, y + bh * 0.6, x + bw - pad - 5, y + bh * 0.6);

        // Gold pile on top
        gc.setFill(Color.rgb(255, 220, 50));
        gc.fillOval(cx - 8, y + bh * 0.12, 7, 5);
        gc.fillOval(cx - 2, y + bh * 0.1, 6, 5);
        gc.fillOval(cx + 4, y + bh * 0.13, 5, 4);

        // Lock/keyhole
        gc.setFill(Color.rgb(80, 60, 30));
        gc.fillOval(cx - 3, y + bh * 0.5, 6, 8);
        gc.setFill(Color.rgb(50, 35, 15));
        gc.fillRect(cx - 1, y + bh * 0.55, 2, 4);

        // Highlight
        gc.setFill(Color.rgb(255, 255, 200, 0.2));
        gc.fillRoundRect(x + pad + 5, y + bh * 0.22, bw * 0.3, bh * 0.25, 6, 6);

        // Level badge
        drawLevelBadge(gc, x + bw - 13, y + bh - 13, level, Color.rgb(200, 150, 30));

        // Border
        gc.setStroke(Color.rgb(140, 100, 30));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
    }

    /**
     * Draw Elixir Storage: large purple tank with glass dome and bubbles.
     */
    private void drawElixirStorage(GraphicsContext gc, double x, double y, double bw, double bh, int level) {
        double cx = x + bw / 2;
        double pad = 2;

        // Base platform
        gc.setFill(Color.rgb(100, 80, 110));
        gc.fillRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);

        // Tank body (dark purple)
        gc.setFill(Color.rgb(120, 50, 140));
        gc.fillRoundRect(x + pad + 3, y + bh * 0.25, bw - pad * 2 - 6, bh * 0.68, 10, 10);

        // Liquid fill (lighter purple)
        gc.setFill(Color.rgb(170, 80, 200));
        gc.fillRoundRect(x + pad + 5, y + bh * 0.4, bw - pad * 2 - 10, bh * 0.5, 8, 8);

        // Glass dome top
        gc.setFill(Color.rgb(180, 130, 210, 0.7));
        gc.fillOval(x + bw * 0.2, y + bh * 0.05, bw * 0.6, bh * 0.35);

        // Metal rim
        gc.setFill(Color.rgb(130, 130, 140));
        gc.fillRect(x + pad + 2, y + bh * 0.23, bw - pad * 2 - 4, bh * 0.05);

        // Metal bands
        gc.setStroke(Color.rgb(100, 90, 110));
        gc.setLineWidth(1.5);
        gc.strokeLine(x + pad + 4, y + bh * 0.5, x + bw - pad - 4, y + bh * 0.5);
        gc.strokeLine(x + pad + 4, y + bh * 0.7, x + bw - pad - 4, y + bh * 0.7);

        // Bubbles in liquid
        gc.setFill(Color.rgb(210, 170, 240, 0.6));
        gc.fillOval(cx - 4, y + bh * 0.48, 5, 5);
        gc.fillOval(cx + 5, y + bh * 0.54, 3, 3);
        gc.fillOval(cx - 7, y + bh * 0.6, 4, 4);

        // Level badge
        drawLevelBadge(gc, x + bw - 13, y + bh - 13, level, Color.rgb(100, 40, 130));

        // Border
        gc.setStroke(Color.rgb(80, 40, 100));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
    }

    /**
     * Draw Barracks: military training building with crossed-sword emblem.
     */
    private void drawBarracks(GraphicsContext gc, double x, double y, double bw, double bh, int level) {
        double cx = x + bw / 2;
        double pad = 2;

        // Ground
        gc.setFill(Color.rgb(120, 100, 70));
        gc.fillRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);

        // Building body (dark brown stone)
        gc.setFill(Color.rgb(140, 90, 45));
        gc.fillRect(x + bw * 0.1, y + bh * 0.3, bw * 0.8, bh * 0.65);

        // Roof (reddish-brown trapezoid)
        gc.setFill(Color.rgb(130, 55, 30));
        gc.fillPolygon(
            new double[]{x + bw * 0.05, x + bw * 0.95, x + bw * 0.85, x + bw * 0.15},
            new double[]{y + bh * 0.32, y + bh * 0.32, y + bh * 0.15, y + bh * 0.15}, 4);

        // Roof peak
        gc.setFill(Color.rgb(150, 65, 35));
        gc.fillPolygon(
            new double[]{x + bw * 0.15, x + bw * 0.85, cx},
            new double[]{y + bh * 0.15, y + bh * 0.15, y + bh * 0.05}, 3);

        // Door (arched entry)
        gc.setFill(Color.rgb(60, 35, 15));
        double doorW = bw * 0.22;
        double doorH = bh * 0.25;
        gc.fillRect(cx - doorW / 2, y + bh - pad - doorH, doorW, doorH);
        gc.fillOval(cx - doorW / 2, y + bh - pad - doorH - 3, doorW, 8);

        // Crossed swords emblem
        gc.setStroke(Color.rgb(200, 200, 210));
        gc.setLineWidth(2);
        gc.strokeLine(cx - 8, y + bh * 0.4, cx + 8, y + bh * 0.57);
        gc.strokeLine(cx + 8, y + bh * 0.4, cx - 8, y + bh * 0.57);

        // Shield behind swords
        gc.setFill(Color.rgb(180, 50, 40));
        gc.fillOval(cx - 6, y + bh * 0.43, 12, 12);

        // Torches on sides of door
        gc.setFill(Color.rgb(255, 150, 30));
        gc.fillOval(cx - doorW / 2 - 5, y + bh * 0.62, 4, 6);
        gc.fillOval(cx + doorW / 2 + 1, y + bh * 0.62, 4, 6);
        gc.setFill(Color.rgb(255, 200, 50));
        gc.fillOval(cx - doorW / 2 - 4, y + bh * 0.61, 2, 3);
        gc.fillOval(cx + doorW / 2 + 2, y + bh * 0.61, 2, 3);

        // Level badge
        drawLevelBadge(gc, x + bw - 13, y + bh - 13, level, Color.rgb(130, 55, 30));

        // Border
        gc.setStroke(Color.rgb(90, 60, 30));
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
    }

    /**
     * Draw Army Camp: tents around a campfire with a flag.
     */
    private void drawArmyCamp(GraphicsContext gc, double x, double y, double bw, double bh, int level) {
        double cx = x + bw / 2;
        double cy = y + bh / 2;
        double pad = 2;

        // Sandy ground
        gc.setFill(Color.rgb(160, 145, 100));
        gc.fillRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);

        // Darker dirt patches
        gc.setFill(Color.rgb(140, 125, 85));
        gc.fillOval(x + bw * 0.1, y + bh * 0.6, bw * 0.3, bh * 0.2);
        gc.fillOval(x + bw * 0.5, y + bh * 0.2, bw * 0.35, bh * 0.2);

        // Tent 1 (top-left, green)
        gc.setFill(Color.rgb(80, 120, 50));
        gc.fillPolygon(
            new double[]{x + bw * 0.05, x + bw * 0.35, x + bw * 0.2},
            new double[]{y + bh * 0.4, y + bh * 0.4, y + bh * 0.12}, 3);
        gc.setFill(Color.rgb(60, 95, 35));
        gc.fillRect(x + bw * 0.05, y + bh * 0.36, bw * 0.3, bh * 0.06);

        // Tent 2 (top-right, brownish)
        gc.setFill(Color.rgb(140, 100, 55));
        gc.fillPolygon(
            new double[]{x + bw * 0.6, x + bw * 0.9, x + bw * 0.75},
            new double[]{y + bh * 0.4, y + bh * 0.4, y + bh * 0.12}, 3);
        gc.setFill(Color.rgb(120, 85, 45));
        gc.fillRect(x + bw * 0.6, y + bh * 0.36, bw * 0.3, bh * 0.06);

        // Tent 3 (bottom, olive)
        gc.setFill(Color.rgb(100, 110, 55));
        gc.fillPolygon(
            new double[]{x + bw * 0.25, x + bw * 0.65, x + bw * 0.45},
            new double[]{y + bh * 0.85, y + bh * 0.85, y + bh * 0.55}, 3);
        gc.setFill(Color.rgb(85, 95, 45));
        gc.fillRect(x + bw * 0.25, y + bh * 0.81, bw * 0.4, bh * 0.06);

        // Campfire (center)
        gc.setFill(Color.rgb(180, 80, 20));
        gc.fillOval(cx - 5, cy - 2, 10, 8);
        gc.setFill(Color.rgb(255, 160, 30));
        gc.fillOval(cx - 3, cy, 6, 5);
        gc.setFill(Color.rgb(255, 220, 80));
        gc.fillOval(cx - 1, cy + 1, 3, 3);

        // Flag pole
        gc.setStroke(Color.rgb(100, 80, 50));
        gc.setLineWidth(1.5);
        gc.strokeLine(x + bw * 0.85, y + bh * 0.12, x + bw * 0.85, y + bh * 0.55);
        // Flag
        gc.setFill(Color.rgb(200, 50, 40));
        gc.fillPolygon(
            new double[]{x + bw * 0.85, x + bw * 0.85, x + bw * 0.72},
            new double[]{y + bh * 0.12, y + bh * 0.25, y + bh * 0.185}, 3);

        // Level badge
        drawLevelBadge(gc, x + bw - 13, y + bh - 13, level, Color.rgb(100, 110, 50));

        // Border
        gc.setStroke(Color.rgb(110, 95, 60));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
    }

    /**
     * Draw Laboratory: stone building with teal dome and colored smoke.
     */
    private void drawLaboratory(GraphicsContext gc, double x, double y, double bw, double bh, int level) {
        double cx = x + bw / 2;
        double pad = 2;

        // Stone base
        gc.setFill(Color.rgb(90, 100, 100));
        gc.fillRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);

        // Building body (stone)
        gc.setFill(Color.rgb(110, 125, 120));
        gc.fillRect(x + bw * 0.1, y + bh * 0.35, bw * 0.8, bh * 0.6);

        // Dome/arched roof (teal)
        gc.setFill(Color.rgb(60, 130, 120));
        gc.fillOval(x + bw * 0.1, y + bh * 0.08, bw * 0.8, bh * 0.45);

        // Dome highlight
        gc.setFill(Color.rgb(80, 160, 145, 0.6));
        gc.fillOval(x + bw * 0.2, y + bh * 0.1, bw * 0.35, bh * 0.2);

        // Chimney
        gc.setFill(Color.rgb(80, 80, 85));
        gc.fillRect(x + bw * 0.72, y + bh * 0.08, bw * 0.12, bh * 0.3);

        // Smoke puffs
        gc.setFill(Color.rgb(100, 200, 180, 0.5));
        gc.fillOval(x + bw * 0.72, y + bh * 0.01, bw * 0.12, bh * 0.1);
        gc.setFill(Color.rgb(120, 220, 200, 0.35));
        gc.fillOval(x + bw * 0.68, y + pad, bw * 0.1, bh * 0.08);

        // Glowing teal window
        gc.setFill(Color.rgb(100, 240, 210));
        gc.fillRoundRect(cx - bw * 0.12, y + bh * 0.5, bw * 0.24, bh * 0.15, 3, 3);
        gc.setFill(Color.rgb(150, 255, 230, 0.5));
        gc.fillRoundRect(cx - bw * 0.08, y + bh * 0.52, bw * 0.1, bh * 0.08, 2, 2);

        // Door
        gc.setFill(Color.rgb(55, 70, 65));
        double doorW = bw * 0.18;
        double doorH = bh * 0.2;
        gc.fillRect(cx - doorW / 2, y + bh - pad - doorH, doorW, doorH);

        // Potion bottles
        gc.setFill(Color.rgb(50, 200, 100));
        gc.fillOval(x + bw * 0.15, y + bh * 0.65, 5, 7);
        gc.setFill(Color.rgb(200, 80, 200));
        gc.fillOval(x + bw * 0.25, y + bh * 0.67, 4, 6);
        gc.setFill(Color.rgb(250, 200, 50));
        gc.fillOval(x + bw * 0.7, y + bh * 0.66, 5, 7);

        // Level badge
        drawLevelBadge(gc, x + bw - 13, y + bh - 13, level, Color.rgb(50, 120, 110));

        // Border
        gc.setStroke(Color.rgb(60, 80, 75));
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
    }

    /**
     * Draw a small level badge circle with the level number.
     */
    private void drawLevelBadge(GraphicsContext gc, double bx, double by, int level, Color bgColor) {
        gc.setFill(bgColor);
        gc.fillOval(bx, by, 12, 12);
        gc.setStroke(Color.rgb(255, 215, 0));
        gc.setLineWidth(1);
        gc.strokeOval(bx, by, 12, 12);
        gc.setFill(Color.rgb(255, 255, 200));
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        gc.fillText("" + level, bx + (level >= 10 ? 1 : 3), by + 10);
    }

    // ─── Event Handlers ──────────────────────────────────────────────

    /**
     * Handle canvas click: select existing building, place new one, or move building.
     */
    private void onCanvasClicked(MouseEvent event) {
        int gridX = (int) (event.getX() / cellWidth);
        int gridY = (int) (event.getY() / cellHeight);

        if (moveMode && movingBuilding != null) {
            // Move mode — send move request to server
            gameEngine.moveBuilding(movingBuilding.getId(), gridX, gridY);
            buildingInfoLabel.setText("Moving " + movingBuilding.getType() + "...");
            moveMode = false;
            movingBuilding = null;
            // renderBase() will be triggered by server UPDATE_BASE response
        } else if (placementMode && selectedBuildingType != null) {
            // Place building mode — send to server; server responds with full UPDATE_BASE
            gameEngine.placeBuilding(selectedBuildingType, gridX, gridY);
            placementMode = false;
            buildingInfoLabel.setText("Placing " + selectedBuildingType + "...");
            // renderBase() will be triggered automatically by the baseUpdateListener
        } else {
            // Select existing building
            selectBuildingAt(gridX, gridY);
        }
    }

    /**
     * Select a building at the given grid position.
     * Shows building info + upgrade prerequisites.
     */
    private void selectBuildingAt(int gridX, int gridY) {
        selectedBuilding = null;
        Base base = getCurrentBase();
        if (base != null) {
            for (Building b : base.getBuildings()) {
                if (gridX >= b.getGridX() && gridX < b.getGridX() + b.getWidth() &&
                    gridY >= b.getGridY() && gridY < b.getGridY() + b.getHeight()) {
                    selectedBuilding = b;
                    break;
                }
            }
        }

        if (selectedBuilding != null) {
            int upgradeCost = selectedBuilding.getLevel() * 200 + 100;
            String prereqInfo = getUpgradePrerequisiteInfo(selectedBuilding, base);
            String category = getBuildingCategory(selectedBuilding.getType());
            String extraInfo = getBuildingExtraInfo(selectedBuilding);
            String upgradingStatus = "";
            if (selectedBuilding.isUpgrading()) {
                int timeLeft = selectedBuilding.getUpgradeTimeRemaining();
                int gemCost = Math.max(1, timeLeft / 10);
                upgradingStatus = String.format("\n\u2692 UPGRADING... %ds remaining\n\uD83D\uDC8E %d gems to speed up", timeLeft, gemCost);
            }
            buildingInfoLabel.setText(String.format(
                    "%s (Level %d)\n%s\nHP: %d/%d%s\nUpgrade cost: %d gold\n%s%s",
                    selectedBuilding.getType().replace("_", " "), selectedBuilding.getLevel(),
                    category,
                    selectedBuilding.getCurrentHitPoints(), selectedBuilding.getHitPoints(),
                    extraInfo,
                    upgradeCost, prereqInfo, upgradingStatus
            ));
            upgradeButton.setDisable(selectedBuilding.isUpgrading());
            removeButton.setDisable(selectedBuilding.isUpgrading());
            moveButton.setDisable(selectedBuilding.isUpgrading());
            speedUpButton.setDisable(!selectedBuilding.isUpgrading());
        } else {
            buildingInfoLabel.setText("Click a building to select it");
            upgradeButton.setDisable(true);
            removeButton.setDisable(true);
            moveButton.setDisable(true);
            speedUpButton.setDisable(true);
        }

        renderBase();
    }

    /**
     * Compute upgrade prerequisite text for the selected building.
     */
    private String getUpgradePrerequisiteInfo(Building building, Base base) {
        if (base == null) return "";
        int thLevel = base.getBuildings().stream()
                .filter(b -> "TOWN_HALL".equals(b.getType()))
                .mapToInt(Building::getLevel).max().orElse(1);

        if ("TOWN_HALL".equals(building.getType())) {
            // TH upgrade requires all non-wall buildings >= current TH level
            int currentTH = building.getLevel();
            long notReady = base.getBuildings().stream()
                    .filter(b -> !"TOWN_HALL".equals(b.getType()) && !"WALL".equals(b.getType()))
                    .filter(b -> b.getLevel() < currentTH)
                    .count();
            if (notReady > 0) {
                return "\u26A0 Upgrade all buildings to Lv" + currentTH + " first! (" + notReady + " remaining)";
            }
            return "\u2705 Ready to upgrade!";
        } else if (!"WALL".equals(building.getType())) {
            // Non-TH buildings capped by TH level
            if (building.getLevel() >= thLevel) {
                return "\u26A0 Upgrade Town Hall to Lv" + (thLevel + 1) + " first!";
            }
            int maxLevel = thLevel * 3;
            return "\u2705 Can upgrade (max Lv" + maxLevel + " at TH" + thLevel + ")";
        }
        return "";
    }

    /**
     * Get the building category label for display.
     */
    private String getBuildingCategory(String type) {
        return switch (type) {
            case "TOWN_HALL" -> "\uD83C\uDFE0 Core Building";
            case "GOLD_MINE", "ELIXIR_COLLECTOR" -> "\u26CF Resource Production";
            case "GOLD_STORAGE", "ELIXIR_STORAGE" -> "\uD83D\uDCE6 Storage";
            case "CANNON", "ARCHER_TOWER", "MORTAR", "WALL" -> "\uD83D\uDEE1 Defense";
            case "BARRACKS", "ARMY_CAMP", "LABORATORY" -> "\u2694 Army";
            default -> "";
        };
    }

    /**
     * Get extra info about a building's special stats.
     */
    private String getBuildingExtraInfo(Building building) {
        String type = building.getType();
        StringBuilder sb = new StringBuilder();
        if (building.getDefenseValue() > 0) {
            sb.append("\nDefense: ").append(building.getDefenseValue());
        }
        if (building.getProductionRate() > 0) {
            sb.append("\nProduction: ").append(building.getProductionRate()).append("/min");
        }
        if ("GOLD_STORAGE".equals(type)) {
            int cap = building.getStorageCapacity() > 0 ? building.getStorageCapacity() : 5000 * building.getLevel();
            sb.append("\nCapacity: ").append(cap).append(" gold");
        } else if ("ELIXIR_STORAGE".equals(type)) {
            int cap = building.getStorageCapacity() > 0 ? building.getStorageCapacity() : 5000 * building.getLevel();
            sb.append("\nCapacity: ").append(cap).append(" elixir");
        } else if ("ARMY_CAMP".equals(type)) {
            int housing = building.getStorageCapacity() > 0 ? building.getStorageCapacity() : 30;
            sb.append("\nHousing: ").append(housing).append(" units");
        } else if ("BARRACKS".equals(type)) {
            sb.append("\nTroop tiers unlocked: ").append(building.getLevel());
        }
        return sb.toString();
    }

    @FXML
    private void onPlaceBuildingClicked() {
        selectedBuildingType = buildingTypeCombo.getValue();
        if (selectedBuildingType == null) {
            UIUtils.showWarning("Select Building", "Please select a building type first.");
            return;
        }
        placementMode = true;
        buildingInfoLabel.setText("Click on the grid to place " + selectedBuildingType);
    }

    @FXML
    private void onUpgradeClicked() {
        if (selectedBuilding != null) {
            // Check prerequisites client-side first for instant feedback
            Base base = getCurrentBase();
            String prereq = getUpgradePrerequisiteInfo(selectedBuilding, base);
            if (prereq.contains("\u26A0")) {
                UIUtils.showWarning("Cannot Upgrade", prereq.replace("\u26A0 ", ""));
                return;
            }
            // Send upgrade request to server
            gameEngine.upgradeBuilding(selectedBuilding.getId());
            buildingInfoLabel.setText("Upgrading " + selectedBuilding.getType().replace("_", " ") + "...");
        }
    }

    @FXML
    private void onRemoveClicked() {
        if (selectedBuilding != null && selectedBuilding.getType().equals("TOWN_HALL")) {
            UIUtils.showWarning("Cannot Remove", "Town Hall cannot be removed.");
            return;
        }
        if (selectedBuilding != null) {
            // Send remove request to server
            String payload = "{\"buildingId\":" + selectedBuilding.getId() + "}";
            gameEngine.getNetworkClient().sendMessage(new GameMessage(
                    GameMessage.MessageType.REMOVE_BUILDING,
                    gameEngine.getNetworkClient().getPlayerId(),
                    payload));
            selectedBuilding = null;
            buildingInfoLabel.setText("Removing building...");
            // renderBase() will be triggered by server UPDATE_BASE response
        }
    }

    @FXML
    private void onMoveClicked() {
        if (selectedBuilding != null) {
            if ("TOWN_HALL".equals(selectedBuilding.getType())) {
                UIUtils.showWarning("Cannot Move", "Town Hall cannot be moved.");
                return;
            }
            moveMode = true;
            movingBuilding = selectedBuilding;
            buildingInfoLabel.setText("Click on the grid to move " + selectedBuilding.getType() + " to a new position");
        }
    }

    @FXML
    private void onSpeedUpClicked() {
        if (selectedBuilding != null && selectedBuilding.isUpgrading()) {
            gameEngine.speedUpBuildingUpgrade(selectedBuilding.getId());
            buildingInfoLabel.setText("Speeding up upgrade...");
        }
    }

    @FXML
    private void onBackClicked() {
        gameEngine.navigateTo("dashboard");
    }
}
