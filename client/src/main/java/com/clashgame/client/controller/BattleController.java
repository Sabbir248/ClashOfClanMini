package com.clashgame.client.controller;

import com.clashgame.client.game.GameEngine;
import com.clashgame.client.game.GameEngine.BattleBuildingInfo;
import com.clashgame.client.game.GameEngine.BattleUnitInfo;
import com.clashgame.client.game.GameEngine.DefenseFireInfo;
import com.clashgame.client.model.Building;
import com.clashgame.client.model.Player;
import com.clashgame.client.model.Unit;
import com.clashgame.client.util.UIUtils;
import javafx.animation.AnimationTimer;
import javafx.animation.ScaleTransition;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller for the battle screen.
 * Renders the defender's base, allows unit deployment, and visualizes combat
 * with animated troop characters, building health bars, and attack effects.
 * 
 * Battle Flow:
 * 1. Defender's base is rendered on the canvas
 * 2. Player selects units from the army tray at the bottom
 * 3. Player taps/clicks on the grid edge to deploy units
 * 4. Units move and fight automatically (server-driven animation)
 * 5. HUD shows timer, destruction %, stars
 * 
 * FXML: battle_screen.fxml
 */
public class BattleController {

    @FXML private Canvas battleCanvas;
    @FXML private Label timerLabel;
    @FXML private Label destructionLabel;
    @FXML private Label starsLabel;
    @FXML private Label unitCountLabel;
    @FXML private HBox unitCardsTray;
    @FXML private ProgressBar destructionBar;
    @FXML private Button surrenderButton;

    private final GameEngine gameEngine = GameEngine.getInstance();

    // Currently selected unit type for deployment
    private String selectedUnitType = null;
    private VBox selectedUnitCard = null;

    // Troop emojis (same as Laboratory/Train)
    private static final java.util.Map<String, String> TROOP_EMOJIS = java.util.Map.of(
            "BARBARIAN", "⚔", "ARCHER", "🏹", "GIANT", "🛡",
            "GOBLIN", "💰", "WIZARD", "✨", "DRAGON", "🐉", "HEALER", "❤"
    );
    private static final java.util.Map<String, String> TROOP_COLORS = java.util.Map.of(
            "BARBARIAN", "#ff9800", "ARCHER", "#e91e63", "GIANT", "#795548",
            "GOBLIN", "#4caf50", "WIZARD", "#9c27b0", "DRAGON", "#f44336", "HEALER", "#e91e63"
    );

    // Battle state
    private double cellWidth;
    private double cellHeight;
    private int elapsedSeconds = 0;
    private double destructionPct = 0;
    private int starsEarned = 0;

    // Battle update listener references (for cleanup)
    private ChangeListener<Number> elapsedListener;
    private ChangeListener<Number> destructionListener;
    private ListChangeListener<BattleUnitInfo> unitsChangeListener;
    private ListChangeListener<BattleBuildingInfo> buildingsChangeListener;
    private ListChangeListener<DefenseFireInfo> fireEventsChangeListener;

    // Cached snapshot of unit/building state for rendering
    private List<BattleUnitInfo> renderedUnits = new ArrayList<>();
    private List<BattleBuildingInfo> renderedBuildings = new ArrayList<>();

    // Active projectile effects (with decay timer for animation)
    private final List<ActiveProjectile> activeProjectiles = new ArrayList<>();
    private static final double PROJECTILE_LIFETIME = 0.35; // seconds

    // Animation timer for smooth rendering
    private AnimationTimer battleAnimationTimer;
    private boolean needsRedraw = true;

    // Attack flash effect state
    private double flashTimer = 0;
    private static final double FLASH_DURATION = 0.15;

    // Previous star count for animation
    private int previousStars = 0;

    // Fonts for troop characters
    private final Font troopFont = Font.font("Segoe UI Emoji", FontWeight.BOLD, 16);
    private final Font troopLabelFont = Font.font("Arial", FontWeight.BOLD, 9);
    private final Font buildingLabelFont = Font.font("Arial", FontWeight.BOLD, 10);

    // ─── Initialization ──────────────────────────────────────────────

    @FXML
    public void initialize() {
        cellWidth = battleCanvas.getWidth() / 20.0;
        cellHeight = battleCanvas.getHeight() / 20.0;

        // Populate unit selector with available army units (show counts)
        populateUnitSelector();

        // Canvas click to deploy units
        battleCanvas.setOnMouseClicked(this::onBattleCanvasClicked);

        // Update unit count from player's army
        updateUnitCount();

        // Listen for elapsed time changes → update HUD
        elapsedListener = (obs, oldVal, newVal) -> {
            this.elapsedSeconds = newVal.intValue();
            updateHUD();
        };
        gameEngine.battleElapsedProperty().addListener(elapsedListener);

        // Listen for destruction changes → update HUD + stars
        destructionListener = (obs, oldVal, newVal) -> {
            this.destructionPct = newVal.doubleValue();
            this.starsEarned = destructionPct >= 100 ? 3 : destructionPct >= 75 ? 2 : destructionPct >= 50 ? 1 : 0;
            updateHUD();
        };
        gameEngine.battleDestructionProperty().addListener(destructionListener);

        // Listen for live unit position updates → snapshot & flag redraw
        unitsChangeListener = change -> {
            renderedUnits = new ArrayList<>(gameEngine.getBattleUnits());
            flashTimer = FLASH_DURATION; // trigger attack flash
            needsRedraw = true;
        };
        gameEngine.getBattleUnits().addListener(unitsChangeListener);

        // Listen for live building state updates → snapshot & flag redraw
        buildingsChangeListener = change -> {
            renderedBuildings = new ArrayList<>(gameEngine.getBattleBuildingStates());
            needsRedraw = true;
        };
        gameEngine.getBattleBuildingStates().addListener(buildingsChangeListener);

        // Listen for defense fire events → create animated projectiles
        fireEventsChangeListener = change -> {
            for (DefenseFireInfo fe : gameEngine.getDefenseFireEvents()) {
                activeProjectiles.add(new ActiveProjectile(
                        fe.defenseType, fe.fromX, fe.fromY, fe.toX, fe.toY, fe.damage,
                        PROJECTILE_LIFETIME));
            }
            needsRedraw = true;
        };
        gameEngine.getDefenseFireEvents().addListener(fireEventsChangeListener);

        // Start animation timer for smooth 30fps rendering
        startAnimationTimer();

        // Initial render with defender buildings (before first BATTLE_UPDATE arrives)
        renderBattleField();
        updateHUD();
    }

    /**
     * Start the animation timer for smooth battle rendering.
     */
    private void startAnimationTimer() {
        final long[] lastFrame = {System.nanoTime()};
        battleAnimationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double dt = (now - lastFrame[0]) / 1_000_000_000.0;
                lastFrame[0] = now;

                // Decay flash timer
                if (flashTimer > 0) {
                    flashTimer -= dt;
                    needsRedraw = true;
                }

                // Decay projectile lifetimes
                if (!activeProjectiles.isEmpty()) {
                    activeProjectiles.removeIf(p -> {
                        p.remainingTime -= dt;
                        return p.remainingTime <= 0;
                    });
                    needsRedraw = true;
                }

                // Only redraw when data has changed
                if (needsRedraw) {
                    needsRedraw = false;
                    renderBattleField();
                }
            }
        };
        battleAnimationTimer.start();
    }

    // ─── Rendering ───────────────────────────────────────────────────

    /**
     * Render the full battle field: terrain → buildings → units → effects.
     */
    private void renderBattleField() {
        GraphicsContext gc = battleCanvas.getGraphicsContext2D();
        double w = battleCanvas.getWidth();
        double h = battleCanvas.getHeight();

        // ── Layer 0: Background ──
        gc.setFill(Color.rgb(55, 110, 20));
        gc.fillRect(0, 0, w, h);

        // Subtle grass texture lines
        gc.setStroke(Color.rgb(50, 100, 18, 0.5));
        gc.setLineWidth(0.3);
        for (int i = 0; i < 40; i++) {
            double y = i * h / 40;
            gc.strokeLine(0, y, w, y);
        }

        // Grid
        UIUtils.drawGrid(battleCanvas, 20, 20);

        // Deployment zone (cyan edge border)
        gc.setStroke(Color.rgb(0, 255, 255, 0.4));
        gc.setLineWidth(cellWidth);
        gc.strokeRect(cellWidth / 2, cellHeight / 2, w - cellWidth, h - cellHeight);

        // ── Layer 1: Buildings (with health bars and damage states) ──
        if (!renderedBuildings.isEmpty()) {
            renderBuildingsFromUpdate(gc);
        } else {
            // Fallback: use initial defender buildings before first update
            renderInitialBuildings(gc);
        }

        // ── Layer 2: Units (animated troop characters) ──
        renderUnits(gc);

        // ── Layer 3: Defense fire projectiles ──
        renderProjectiles(gc);

        // ── Layer 4: Attack flash overlay ──
        if (flashTimer > 0) {
            double alpha = flashTimer / FLASH_DURATION * 0.08;
            gc.setFill(Color.rgb(255, 200, 50, alpha));
            gc.fillRect(0, 0, w, h);
        }
    }

    /**
     * Render buildings from the initial BATTLE_START data (before server sends BATTLE_UPDATE).
     */
    private void renderInitialBuildings(GraphicsContext gc) {
        for (Building building : gameEngine.getDefenderBuildings()) {
            double x = building.getGridX() * cellWidth;
            double y = building.getGridY() * cellHeight;
            double bw = building.getWidth() * cellWidth;
            double bh = building.getHeight() * cellHeight;

            String type = building.getType();
            int level = building.getLevel();
            drawBuildingDetailed(gc, type, x, y, bw, bh, level, 1.0);
        }
    }

    /**
     * Render buildings from the live BATTLE_UPDATE data, with health bars and destruction effects.
     */
    private void renderBuildingsFromUpdate(GraphicsContext gc) {
        for (BattleBuildingInfo b : renderedBuildings) {
            double x = b.gridX * cellWidth;
            double y = b.gridY * cellHeight;
            double bw = b.width * cellWidth;
            double bh = b.height * cellHeight;

            if (b.destroyed) {
                // Rubble: darker background with debris
                gc.setFill(Color.rgb(70, 60, 50, 0.8));
                gc.fillRoundRect(x + 1, y + 1, bw - 2, bh - 2, 4, 4);
                // Debris pieces
                gc.setFill(Color.rgb(100, 90, 75, 0.6));
                gc.fillRect(x + 3, y + 4, bw * 0.3, bh * 0.2);
                gc.fillRect(x + bw * 0.5, y + bh * 0.5, bw * 0.25, bh * 0.25);
                gc.setFill(Color.rgb(80, 70, 60, 0.5));
                gc.fillOval(x + bw * 0.2, y + bh * 0.4, bw * 0.35, bh * 0.3);
                // Cross lines
                gc.setStroke(Color.rgb(100, 90, 80, 0.5));
                gc.setLineWidth(1.2);
                gc.strokeLine(x + 3, y + 3, x + bw - 3, y + bh - 3);
                gc.strokeLine(x + bw - 3, y + 3, x + 3, y + bh - 3);
                // Smoke wisps
                gc.setFill(Color.rgb(120, 120, 120, 0.35));
                gc.fillOval(x + bw * 0.25, y - 4, bw * 0.3, bh * 0.2);
                gc.fillOval(x + bw * 0.5, y - 2, bw * 0.2, bh * 0.15);
            } else {
                double healthPct = b.maxHp > 0 ? (double) b.hp / b.maxHp : 1.0;

                drawBuildingDetailed(gc, b.type, x, y, bw, bh, 1, healthPct);

                // Health bar (shown when damaged)
                if (healthPct < 1.0) {
                    double barW = bw - 4;
                    double barH = 3;
                    double barY = y + bh - 5;
                    gc.setFill(Color.rgb(60, 0, 0));
                    gc.fillRect(x + 2, barY, barW, barH);
                    Color barColor = healthPct > 0.5 ? Color.LIME :
                                     healthPct > 0.25 ? Color.ORANGE : Color.RED;
                    gc.setFill(barColor);
                    gc.fillRect(x + 2, barY, barW * healthPct, barH);
                }

                // Damage flash border (all buildings)
                if (healthPct < 1.0 && flashTimer > 0) {
                    gc.setStroke(Color.rgb(255, 60, 30, flashTimer / FLASH_DURATION * 0.8));
                    gc.setLineWidth(2);
                    gc.strokeRoundRect(x, y, bw, bh, 6, 6);
                }
            }
        }
    }

    /** Check if a building type is a defense building. */
    private boolean isDefenseBuilding(String type) {
        return "CANNON".equals(type) || "ARCHER_TOWER".equals(type)
                || "MORTAR".equals(type) || "WALL".equals(type);
    }

    /**
     * Draw a detailed building: all building types with brightness dimming for damage.
     * @param healthPct 1.0 = full health, 0.0 = almost destroyed (darkens the drawing)
     */
    private void drawBuildingDetailed(GraphicsContext gc, String type, double x, double y,
                                      double bw, double bh, int level, double healthPct) {
        // Brightness factor based on damage
        double bright = 0.5 + 0.5 * healthPct;
        switch (type) {
            case "CANNON"           -> drawBattleCannon(gc, x, y, bw, bh, bright);
            case "ARCHER_TOWER"     -> drawBattleArcherTower(gc, x, y, bw, bh, bright);
            case "MORTAR"           -> drawBattleMortar(gc, x, y, bw, bh, bright);
            case "WALL"             -> drawBattleWall(gc, x, y, bw, bh, bright);
            case "TOWN_HALL"        -> drawBattleTownHall(gc, x, y, bw, bh, bright);
            case "GOLD_MINE"        -> drawBattleGoldMine(gc, x, y, bw, bh, bright);
            case "ELIXIR_COLLECTOR" -> drawBattleElixirCollector(gc, x, y, bw, bh, bright);
            case "GOLD_STORAGE"     -> drawBattleGoldStorage(gc, x, y, bw, bh, bright);
            case "ELIXIR_STORAGE"   -> drawBattleElixirStorage(gc, x, y, bw, bh, bright);
            case "BARRACKS"         -> drawBattleBarracks(gc, x, y, bw, bh, bright);
            case "ARMY_CAMP"        -> drawBattleArmyCamp(gc, x, y, bw, bh, bright);
            case "LABORATORY"       -> drawBattleLaboratory(gc, x, y, bw, bh, bright);
            default -> {
                gc.setFill(Color.rgb(dim(128, bright), dim(128, bright), dim(128, bright)));
                gc.fillRect(x + 1, y + 1, bw - 2, bh - 2);
                gc.setStroke(Color.rgb(30, 30, 30));
                gc.setLineWidth(1);
                gc.strokeRect(x + 1, y + 1, bw - 2, bh - 2);
            }
        }
    }

    /** Cannon: stone platform with dark barrel. */
    private void drawBattleCannon(GraphicsContext gc, double x, double y, double bw, double bh, double br) {
        double cx = x + bw / 2, cy = y + bh / 2;
        // Platform
        gc.setFill(Color.rgb(dim(140, br), dim(130, br), dim(115, br)));
        gc.fillRoundRect(x + 2, y + 2, bw - 4, bh - 4, 8, 8);
        gc.setFill(Color.rgb(dim(160, br), dim(150, br), dim(135, br)));
        gc.fillRoundRect(x + 4, y + 4, bw - 8, bh - 8, 6, 6);
        // Base circle
        double baseR = Math.min(bw, bh) * 0.3;
        gc.setFill(Color.rgb(dim(60, br), dim(55, br), dim(50, br)));
        gc.fillOval(cx - baseR, cy - baseR, baseR * 2, baseR * 2);
        // Barrel
        double barrelW = bw * 0.42, barrelH = bh * 0.16;
        gc.setFill(Color.rgb(dim(45, br), dim(42, br), dim(38, br)));
        gc.fillRoundRect(cx - 2, cy - barrelH / 2, barrelW, barrelH, 4, 4);
        gc.setFill(Color.rgb(dim(80, br), dim(75, br), dim(68, br)));
        gc.fillRoundRect(cx, cy - barrelH / 2 + 1, barrelW - 4, barrelH / 2 - 1, 3, 3);
        // Muzzle ring
        gc.setStroke(Color.rgb(dim(90, br), dim(85, br), dim(75, br)));
        gc.setLineWidth(1.5);
        gc.strokeOval(cx + barrelW - 5, cy - barrelH / 2 - 1, 5, barrelH + 2);
        // Center rivet
        gc.setFill(Color.rgb(dim(100, br), dim(95, br), dim(88, br)));
        gc.fillOval(cx - 3, cy - 3, 6, 6);
        // Border
        gc.setStroke(Color.rgb(dim(90, br), dim(80, br), dim(70, br)));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x + 2, y + 2, bw - 4, bh - 4, 8, 8);
    }

    /** Archer Tower: tall brown tower with pointed red roof and crenellations. */
    private void drawBattleArcherTower(GraphicsContext gc, double x, double y, double bw, double bh, double br) {
        double cx = x + bw / 2;
        // Tower base
        gc.setFill(Color.rgb(dim(110, br), dim(70, br), dim(35, br)));
        gc.fillRoundRect(x + 5, y + bh * 0.3, bw - 10, bh * 0.68, 4, 4);
        // Tower body
        gc.setFill(Color.rgb(dim(140, br), dim(95, br), dim(50, br)));
        gc.fillRoundRect(x + 7, y + bh * 0.15, bw - 14, bh * 0.6, 3, 3);
        // Platform
        gc.setFill(Color.rgb(dim(120, br), dim(80, br), dim(40, br)));
        gc.fillRect(x + 4, y + bh * 0.12, bw - 8, bh * 0.1);
        // Pointed roof
        gc.setFill(Color.rgb(dim(160, br), dim(50, br), dim(50, br)));
        gc.fillPolygon(
                new double[]{x + 4, x + bw - 4, cx},
                new double[]{y + bh * 0.14, y + bh * 0.14, y + 1},
                3);
        // Window
        gc.setFill(Color.rgb(dim(40, br), dim(30, br), dim(20, br)));
        gc.fillRect(cx - 2, y + bh * 0.45, 4, 6);
        // Crenellations
        for (double cw = x + 5; cw < x + bw - 5; cw += 5) {
            gc.setFill(Color.rgb(dim(130, br), dim(85, br), dim(45, br)));
            gc.fillRect(cw, y + bh * 0.1, 3, 3);
        }
        // Bow line
        gc.setStroke(Color.rgb(dim(90, br), dim(50, br), dim(25, br)));
        gc.setLineWidth(1.5);
        gc.strokeLine(cx - 3, y + bh * 0.2, cx + 3, y + bh * 0.28);
        // Border
        gc.setStroke(Color.rgb(dim(80, br), dim(50, br), dim(25, br)));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x + 5, y + bh * 0.3, bw - 10, bh * 0.68, 4, 4);
    }

    /** Mortar: heavy stone pit with short wide barrel. */
    private void drawBattleMortar(GraphicsContext gc, double x, double y, double bw, double bh, double br) {
        double cx = x + bw / 2, cy = y + bh / 2;
        // Platform
        gc.setFill(Color.rgb(dim(100, br), dim(45, br), dim(30, br)));
        gc.fillRoundRect(x + 2, y + 2, bw - 4, bh - 4, 10, 10);
        gc.setFill(Color.rgb(dim(120, br), dim(55, br), dim(35, br)));
        gc.fillRoundRect(x + 4, y + 4, bw - 8, bh - 8, 8, 8);
        // Pit
        double pitR = Math.min(bw, bh) * 0.32;
        gc.setFill(Color.rgb(dim(50, br), dim(25, br), dim(18, br)));
        gc.fillOval(cx - pitR, cy - pitR, pitR * 2, pitR * 2);
        // Barrel (trapezoid)
        double bBotW = bw * 0.33, bTopW = bw * 0.2, barrH = bh * 0.28;
        gc.setFill(Color.rgb(dim(60, br), dim(35, br), dim(25, br)));
        gc.fillPolygon(
                new double[]{cx - bBotW / 2, cx + bBotW / 2, cx + bTopW / 2, cx - bTopW / 2},
                new double[]{cy, cy, cy - barrH, cy - barrH}, 4);
        // Muzzle opening
        gc.setFill(Color.rgb(dim(25, br), dim(15, br), dim(10, br)));
        gc.fillOval(cx - bTopW / 2, cy - barrH - 2, bTopW, 5);
        // Shell glow
        gc.setFill(Color.rgb(dim(220, br), dim(120, br), dim(30, br), 0.7));
        gc.fillOval(cx - 3, cy - barrH + 3, 6, 6);
        // Rivets
        gc.setFill(Color.rgb(dim(80, br), dim(45, br), dim(30, br)));
        gc.fillOval(x + 7, y + bh - 8, 4, 4);
        gc.fillOval(x + bw - 11, y + bh - 8, 4, 4);
        // Border
        gc.setStroke(Color.rgb(dim(70, br), dim(35, br), dim(22, br)));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x + 2, y + 2, bw - 4, bh - 4, 10, 10);
    }

    /** Wall: brick pattern with mortar lines and 3D highlight. */
    private void drawBattleWall(GraphicsContext gc, double x, double y, double bw, double bh, double br) {
        gc.setFill(Color.rgb(dim(155, br), dim(145, br), dim(130, br)));
        gc.fillRect(x, y, bw, bh);
        // Mortar lines
        gc.setStroke(Color.rgb(dim(120, br), dim(112, br), dim(100, br)));
        gc.setLineWidth(0.7);
        double halfH = bh / 2;
        gc.strokeLine(x, y + halfH, x + bw, y + halfH);
        double halfW = bw / 2;
        gc.strokeLine(x + halfW, y, x + halfW, y + halfH);
        gc.strokeLine(x + halfW / 2, y + halfH, x + halfW / 2, y + bh);
        gc.strokeLine(x + halfW + halfW / 2, y + halfH, x + halfW + halfW / 2, y + bh);
        // Top highlight
        gc.setFill(Color.rgb(255, 255, 255, 0.15));
        gc.fillRect(x, y, bw, 2);
        // Bottom shadow
        gc.setFill(Color.rgb(0, 0, 0, 0.2));
        gc.fillRect(x, y + bh - 2, bw, 2);
        gc.setStroke(Color.rgb(dim(80, br), dim(75, br), dim(65, br)));
        gc.setLineWidth(1);
        gc.strokeRect(x, y, bw, bh);
    }

    // ─── Non-Defense Battle Building Renderers ─────────────────────

    /** Town Hall: grand medieval hall with turrets and golden roof. */
    private void drawBattleTownHall(GraphicsContext gc, double x, double y, double bw, double bh, double br) {
        double cx = x + bw / 2;
        double pad = 2;
        // Stone foundation
        gc.setFill(Color.rgb(dim(130, br), dim(120, br), dim(105, br)));
        gc.fillRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
        // Main building body
        gc.setFill(Color.rgb(dim(195, br), dim(175, br), dim(135, br)));
        gc.fillRect(x + bw * 0.15, y + bh * 0.35, bw * 0.7, bh * 0.6);
        // Side turrets
        double tW = bw * 0.14;
        gc.setFill(Color.rgb(dim(160, br), dim(145, br), dim(115, br)));
        gc.fillRect(x + pad + 2, y + bh * 0.25, tW, bh * 0.7);
        gc.fillRect(x + bw - pad - 2 - tW, y + bh * 0.25, tW, bh * 0.7);
        // Turret caps
        gc.setFill(Color.rgb(dim(140, br), dim(50, br), dim(50, br)));
        double tL = x + pad + 1, tR = tL + tW + 2, tMid = tL + (tW + 2) / 2;
        gc.fillPolygon(new double[]{tL, tR, tMid},
                       new double[]{y + bh * 0.27, y + bh * 0.27, y + bh * 0.15}, 3);
        tL = x + bw - pad - 3 - tW; tR = tL + tW + 2; tMid = tL + (tW + 2) / 2;
        gc.fillPolygon(new double[]{tL, tR, tMid},
                       new double[]{y + bh * 0.27, y + bh * 0.27, y + bh * 0.15}, 3);
        // Roof trapezoid
        gc.setFill(Color.rgb(dim(170, br), dim(110, br), dim(40, br)));
        gc.fillPolygon(
            new double[]{x + bw * 0.12, x + bw * 0.88, x + bw * 0.78, x + bw * 0.22},
            new double[]{y + bh * 0.37, y + bh * 0.37, y + bh * 0.22, y + bh * 0.22}, 4);
        // Roof peak
        gc.setFill(Color.rgb(dim(190, br), dim(130, br), dim(50, br)));
        gc.fillPolygon(
            new double[]{x + bw * 0.22, x + bw * 0.78, cx},
            new double[]{y + bh * 0.22, y + bh * 0.22, y + bh * 0.08}, 3);
        // Crenellations
        for (double cw = x + bw * 0.18; cw < x + bw * 0.82; cw += bw * 0.08) {
            gc.setFill(Color.rgb(dim(175, br), dim(155, br), dim(120, br)));
            gc.fillRect(cw, y + bh * 0.33, bw * 0.05, bh * 0.04);
        }
        // Door
        gc.setFill(Color.rgb(dim(80, br), dim(50, br), dim(25, br)));
        double doorW = bw * 0.16, doorH = bh * 0.25;
        gc.fillRect(cx - doorW / 2, y + bh - pad - doorH, doorW, doorH);
        gc.fillOval(cx - doorW / 2, y + bh - pad - doorH - doorW * 0.15, doorW, doorW * 0.3);
        // Windows
        gc.setFill(Color.rgb(dim(150, br), dim(200, br), dim(255, br)));
        double winW = bw * 0.08, winH = bh * 0.1;
        gc.fillRect(cx - bw * 0.22, y + bh * 0.48, winW, winH);
        gc.fillRect(cx + bw * 0.14, y + bh * 0.48, winW, winH);
        // Gold emblem
        gc.setFill(Color.rgb(dim(255, br), dim(215, br), dim(0, br)));
        gc.fillOval(cx - 4, y + bh * 0.55, 8, 8);
        // Border
        gc.setStroke(Color.rgb(dim(100, br), dim(90, br), dim(70, br)));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
    }

    /** Gold Mine: wooden mine entrance with gold nuggets. */
    private void drawBattleGoldMine(GraphicsContext gc, double x, double y, double bw, double bh, double br) {
        double cx = x + bw / 2;
        double pad = 2;
        // Earth base
        gc.setFill(Color.rgb(dim(120, br), dim(90, br), dim(50, br)));
        gc.fillRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
        // Dirt mound
        gc.setFill(Color.rgb(dim(140, br), dim(105, br), dim(60, br)));
        gc.fillOval(x + pad + 2, y + bh * 0.15, bw - pad * 2 - 4, bh * 0.7);
        // Mine tunnel
        double openW = bw * 0.5, openH = bh * 0.4;
        gc.setFill(Color.rgb(dim(30, br), dim(20, br), dim(10, br)));
        gc.fillRect(cx - openW / 2, y + bh * 0.35, openW, openH);
        gc.fillOval(cx - openW / 2, y + bh * 0.28, openW, openH * 0.3);
        // Wooden frame
        gc.setFill(Color.rgb(dim(160, br), dim(110, br), dim(50, br)));
        gc.fillRect(cx - openW / 2 - 3, y + bh * 0.3, 4, openH + bh * 0.05);
        gc.fillRect(cx + openW / 2 - 1, y + bh * 0.3, 4, openH + bh * 0.05);
        gc.fillRect(cx - openW / 2 - 3, y + bh * 0.28, openW + 6, 4);
        // Gold nuggets
        gc.setFill(Color.rgb(dim(255, br), dim(215, br), dim(0, br)));
        gc.fillOval(x + bw * 0.15, y + bh * 0.7, 6, 5);
        gc.fillOval(x + bw * 0.62, y + bh * 0.72, 5, 4);
        gc.fillOval(cx - 1, y + bh * 0.78, 4, 4);
        // Border
        gc.setStroke(Color.rgb(dim(90, br), dim(70, br), dim(40, br)));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
    }

    /** Elixir Collector: apparatus with purple elixir container. */
    private void drawBattleElixirCollector(GraphicsContext gc, double x, double y, double bw, double bh, double br) {
        double cx = x + bw / 2;
        double pad = 2;
        // Ground
        gc.setFill(Color.rgb(dim(100, br), dim(145, br), dim(60, br)));
        gc.fillRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
        // Wooden base
        gc.setFill(Color.rgb(dim(140, br), dim(100, br), dim(55, br)));
        gc.fillRoundRect(x + pad, y + bh * 0.7, bw - pad * 2, bh * 0.28, 4, 4);
        // Elixir container
        gc.setFill(Color.rgb(dim(130, br), dim(60, br), dim(160, br)));
        gc.fillRoundRect(cx - bw * 0.28, y + bh * 0.12, bw * 0.56, bh * 0.62, 8, 8);
        // Liquid level
        gc.setFill(Color.rgb(dim(170, br), dim(80, br), dim(220, br)));
        gc.fillRoundRect(cx - bw * 0.22, y + bh * 0.4, bw * 0.44, bh * 0.3, 6, 6);
        // Glass highlight
        gc.setFill(Color.rgb(dim(180, br), dim(130, br), dim(210, br), 0.5));
        gc.fillRoundRect(cx - bw * 0.18, y + bh * 0.16, bw * 0.2, bh * 0.3, 4, 4);
        // Metal rims
        gc.setFill(Color.rgb(dim(120, br), dim(120, br), dim(130, br)));
        gc.fillRect(cx - bw * 0.3, y + bh * 0.1, bw * 0.6, bh * 0.06);
        gc.fillRect(cx - bw * 0.3, y + bh * 0.7, bw * 0.6, bh * 0.06);
        // Pipe
        gc.setFill(Color.rgb(dim(110, br), dim(110, br), dim(120, br)));
        gc.fillRect(x + bw * 0.72, y + bh * 0.4, bw * 0.18, bh * 0.08);
        // Border
        gc.setStroke(Color.rgb(dim(80, br), dim(50, br), dim(100, br)));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
    }

    /** Gold Storage: reinforced vault with gold piles. */
    private void drawBattleGoldStorage(GraphicsContext gc, double x, double y, double bw, double bh, double br) {
        double cx = x + bw / 2;
        double pad = 2;
        // Base
        gc.setFill(Color.rgb(dim(140, br), dim(120, br), dim(80, br)));
        gc.fillRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
        // Vault body
        gc.setFill(Color.rgb(dim(210, br), dim(160, br), dim(50, br)));
        gc.fillRoundRect(x + pad + 3, y + bh * 0.2, bw - pad * 2 - 6, bh * 0.72, 8, 8);
        // Dome top
        gc.setFill(Color.rgb(dim(230, br), dim(180, br), dim(60, br)));
        gc.fillOval(x + bw * 0.15, y + bh * 0.08, bw * 0.7, bh * 0.35);
        // Metal bands
        gc.setStroke(Color.rgb(dim(140, br), dim(100, br), dim(30, br)));
        gc.setLineWidth(2);
        gc.strokeLine(x + pad + 5, y + bh * 0.4, x + bw - pad - 5, y + bh * 0.4);
        gc.strokeLine(x + pad + 5, y + bh * 0.6, x + bw - pad - 5, y + bh * 0.6);
        // Gold pile
        gc.setFill(Color.rgb(dim(255, br), dim(220, br), dim(50, br)));
        gc.fillOval(cx - 8, y + bh * 0.12, 7, 5);
        gc.fillOval(cx - 2, y + bh * 0.1, 6, 5);
        gc.fillOval(cx + 4, y + bh * 0.13, 5, 4);
        // Lock
        gc.setFill(Color.rgb(dim(80, br), dim(60, br), dim(30, br)));
        gc.fillOval(cx - 3, y + bh * 0.5, 6, 8);
        // Border
        gc.setStroke(Color.rgb(dim(140, br), dim(100, br), dim(30, br)));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
    }

    /** Elixir Storage: large purple tank with glass dome. */
    private void drawBattleElixirStorage(GraphicsContext gc, double x, double y, double bw, double bh, double br) {
        double cx = x + bw / 2;
        double pad = 2;
        // Base
        gc.setFill(Color.rgb(dim(100, br), dim(80, br), dim(110, br)));
        gc.fillRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
        // Tank body
        gc.setFill(Color.rgb(dim(120, br), dim(50, br), dim(140, br)));
        gc.fillRoundRect(x + pad + 3, y + bh * 0.25, bw - pad * 2 - 6, bh * 0.68, 10, 10);
        // Liquid fill
        gc.setFill(Color.rgb(dim(170, br), dim(80, br), dim(200, br)));
        gc.fillRoundRect(x + pad + 5, y + bh * 0.4, bw - pad * 2 - 10, bh * 0.5, 8, 8);
        // Glass dome
        gc.setFill(Color.rgb(dim(180, br), dim(130, br), dim(210, br), 0.7));
        gc.fillOval(x + bw * 0.2, y + bh * 0.05, bw * 0.6, bh * 0.35);
        // Metal rim
        gc.setFill(Color.rgb(dim(130, br), dim(130, br), dim(140, br)));
        gc.fillRect(x + pad + 2, y + bh * 0.23, bw - pad * 2 - 4, bh * 0.05);
        // Bands
        gc.setStroke(Color.rgb(dim(100, br), dim(90, br), dim(110, br)));
        gc.setLineWidth(1.5);
        gc.strokeLine(x + pad + 4, y + bh * 0.5, x + bw - pad - 4, y + bh * 0.5);
        gc.strokeLine(x + pad + 4, y + bh * 0.7, x + bw - pad - 4, y + bh * 0.7);
        // Bubbles
        gc.setFill(Color.rgb(dim(210, br), dim(170, br), dim(240, br), 0.6));
        gc.fillOval(cx - 4, y + bh * 0.48, 5, 5);
        gc.fillOval(cx + 5, y + bh * 0.54, 3, 3);
        // Border
        gc.setStroke(Color.rgb(dim(80, br), dim(40, br), dim(100, br)));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
    }

    /** Barracks: military training building with crossed-sword emblem. */
    private void drawBattleBarracks(GraphicsContext gc, double x, double y, double bw, double bh, double br) {
        double cx = x + bw / 2;
        double pad = 2;
        // Ground
        gc.setFill(Color.rgb(dim(120, br), dim(100, br), dim(70, br)));
        gc.fillRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
        // Building body
        gc.setFill(Color.rgb(dim(140, br), dim(90, br), dim(45, br)));
        gc.fillRect(x + bw * 0.1, y + bh * 0.3, bw * 0.8, bh * 0.65);
        // Roof trapezoid
        gc.setFill(Color.rgb(dim(130, br), dim(55, br), dim(30, br)));
        gc.fillPolygon(
            new double[]{x + bw * 0.05, x + bw * 0.95, x + bw * 0.85, x + bw * 0.15},
            new double[]{y + bh * 0.32, y + bh * 0.32, y + bh * 0.15, y + bh * 0.15}, 4);
        // Roof peak
        gc.setFill(Color.rgb(dim(150, br), dim(65, br), dim(35, br)));
        gc.fillPolygon(
            new double[]{x + bw * 0.15, x + bw * 0.85, cx},
            new double[]{y + bh * 0.15, y + bh * 0.15, y + bh * 0.05}, 3);
        // Door
        gc.setFill(Color.rgb(dim(60, br), dim(35, br), dim(15, br)));
        double doorW = bw * 0.22, doorH = bh * 0.25;
        gc.fillRect(cx - doorW / 2, y + bh - pad - doorH, doorW, doorH);
        gc.fillOval(cx - doorW / 2, y + bh - pad - doorH - 3, doorW, 8);
        // Crossed swords
        gc.setStroke(Color.rgb(dim(200, br), dim(200, br), dim(210, br)));
        gc.setLineWidth(2);
        gc.strokeLine(cx - 8, y + bh * 0.4, cx + 8, y + bh * 0.57);
        gc.strokeLine(cx + 8, y + bh * 0.4, cx - 8, y + bh * 0.57);
        // Shield
        gc.setFill(Color.rgb(dim(180, br), dim(50, br), dim(40, br)));
        gc.fillOval(cx - 6, y + bh * 0.43, 12, 12);
        // Torches
        gc.setFill(Color.rgb(dim(255, br), dim(150, br), dim(30, br)));
        gc.fillOval(cx - doorW / 2 - 5, y + bh * 0.62, 4, 6);
        gc.fillOval(cx + doorW / 2 + 1, y + bh * 0.62, 4, 6);
        // Border
        gc.setStroke(Color.rgb(dim(90, br), dim(60, br), dim(30, br)));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
    }

    /** Army Camp: tents around a campfire with a flag. */
    private void drawBattleArmyCamp(GraphicsContext gc, double x, double y, double bw, double bh, double br) {
        double cx = x + bw / 2, cy = y + bh / 2;
        double pad = 2;
        // Sandy ground
        gc.setFill(Color.rgb(dim(160, br), dim(145, br), dim(100, br)));
        gc.fillRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
        // Tent 1 (green)
        gc.setFill(Color.rgb(dim(80, br), dim(120, br), dim(50, br)));
        gc.fillPolygon(
            new double[]{x + bw * 0.05, x + bw * 0.35, x + bw * 0.2},
            new double[]{y + bh * 0.4, y + bh * 0.4, y + bh * 0.12}, 3);
        gc.setFill(Color.rgb(dim(60, br), dim(95, br), dim(35, br)));
        gc.fillRect(x + bw * 0.05, y + bh * 0.36, bw * 0.3, bh * 0.06);
        // Tent 2 (brown)
        gc.setFill(Color.rgb(dim(140, br), dim(100, br), dim(55, br)));
        gc.fillPolygon(
            new double[]{x + bw * 0.6, x + bw * 0.9, x + bw * 0.75},
            new double[]{y + bh * 0.4, y + bh * 0.4, y + bh * 0.12}, 3);
        gc.setFill(Color.rgb(dim(120, br), dim(85, br), dim(45, br)));
        gc.fillRect(x + bw * 0.6, y + bh * 0.36, bw * 0.3, bh * 0.06);
        // Tent 3 (olive)
        gc.setFill(Color.rgb(dim(100, br), dim(110, br), dim(55, br)));
        gc.fillPolygon(
            new double[]{x + bw * 0.25, x + bw * 0.65, x + bw * 0.45},
            new double[]{y + bh * 0.85, y + bh * 0.85, y + bh * 0.55}, 3);
        gc.setFill(Color.rgb(dim(85, br), dim(95, br), dim(45, br)));
        gc.fillRect(x + bw * 0.25, y + bh * 0.81, bw * 0.4, bh * 0.06);
        // Campfire
        gc.setFill(Color.rgb(dim(180, br), dim(80, br), dim(20, br)));
        gc.fillOval(cx - 5, cy - 2, 10, 8);
        gc.setFill(Color.rgb(dim(255, br), dim(160, br), dim(30, br)));
        gc.fillOval(cx - 3, cy, 6, 5);
        gc.setFill(Color.rgb(dim(255, br), dim(220, br), dim(80, br)));
        gc.fillOval(cx - 1, cy + 1, 3, 3);
        // Flag pole
        gc.setStroke(Color.rgb(dim(100, br), dim(80, br), dim(50, br)));
        gc.setLineWidth(1.5);
        gc.strokeLine(x + bw * 0.85, y + bh * 0.12, x + bw * 0.85, y + bh * 0.55);
        gc.setFill(Color.rgb(dim(200, br), dim(50, br), dim(40, br)));
        gc.fillPolygon(
            new double[]{x + bw * 0.85, x + bw * 0.85, x + bw * 0.72},
            new double[]{y + bh * 0.12, y + bh * 0.25, y + bh * 0.185}, 3);
        // Border
        gc.setStroke(Color.rgb(dim(110, br), dim(95, br), dim(60, br)));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
    }

    /** Laboratory: stone building with teal dome and colored smoke. */
    private void drawBattleLaboratory(GraphicsContext gc, double x, double y, double bw, double bh, double br) {
        double cx = x + bw / 2;
        double pad = 2;
        // Stone base
        gc.setFill(Color.rgb(dim(90, br), dim(100, br), dim(100, br)));
        gc.fillRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
        // Building body
        gc.setFill(Color.rgb(dim(110, br), dim(125, br), dim(120, br)));
        gc.fillRect(x + bw * 0.1, y + bh * 0.35, bw * 0.8, bh * 0.6);
        // Dome (teal)
        gc.setFill(Color.rgb(dim(60, br), dim(130, br), dim(120, br)));
        gc.fillOval(x + bw * 0.1, y + bh * 0.08, bw * 0.8, bh * 0.45);
        // Dome highlight
        gc.setFill(Color.rgb(dim(80, br), dim(160, br), dim(145, br), 0.6));
        gc.fillOval(x + bw * 0.2, y + bh * 0.1, bw * 0.35, bh * 0.2);
        // Chimney
        gc.setFill(Color.rgb(dim(80, br), dim(80, br), dim(85, br)));
        gc.fillRect(x + bw * 0.72, y + bh * 0.08, bw * 0.12, bh * 0.3);
        // Smoke
        gc.setFill(Color.rgb(dim(100, br), dim(200, br), dim(180, br), 0.5));
        gc.fillOval(x + bw * 0.72, y + bh * 0.01, bw * 0.12, bh * 0.1);
        // Glowing window
        gc.setFill(Color.rgb(dim(100, br), dim(240, br), dim(210, br)));
        gc.fillRoundRect(cx - bw * 0.12, y + bh * 0.5, bw * 0.24, bh * 0.15, 3, 3);
        // Door
        gc.setFill(Color.rgb(dim(55, br), dim(70, br), dim(65, br)));
        double doorW = bw * 0.18, doorH = bh * 0.2;
        gc.fillRect(cx - doorW / 2, y + bh - pad - doorH, doorW, doorH);
        // Potion bottles
        gc.setFill(Color.rgb(dim(50, br), dim(200, br), dim(100, br)));
        gc.fillOval(x + bw * 0.15, y + bh * 0.65, 5, 7);
        gc.setFill(Color.rgb(dim(200, br), dim(80, br), dim(200, br)));
        gc.fillOval(x + bw * 0.25, y + bh * 0.67, 4, 6);
        gc.setFill(Color.rgb(dim(250, br), dim(200, br), dim(50, br)));
        gc.fillOval(x + bw * 0.7, y + bh * 0.66, 5, 7);
        // Border
        gc.setStroke(Color.rgb(dim(60, br), dim(80, br), dim(75, br)));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x + pad, y + pad, bw - pad * 2, bh - pad * 2, 6, 6);
    }

    /** Dim a color channel by a brightness factor (0.5–1.0). */
    private int dim(int channel, double brightness) {
        return Math.min(255, Math.max(0, (int) (channel * brightness)));
    }

    /**
     * Render live units as animated troop characters at their server-reported positions.
     */
    private void renderUnits(GraphicsContext gc) {
        for (BattleUnitInfo unit : renderedUnits) {
            double ux = unit.x * cellWidth;
            double uy = unit.y * cellHeight;

            // Unit size varies by type
            double size = getUnitSize(unit.type);
            double halfSize = size / 2;

            // Drop shadow
            gc.setFill(Color.rgb(0, 0, 0, 0.3));
            gc.fillOval(ux - halfSize + 1, uy - halfSize + 3, size, size * 0.4);

            // Unit body circle with type-specific color
            Color unitColor = getUnitColor(unit.type);
            gc.setFill(unitColor);
            gc.fillOval(ux - halfSize, uy - halfSize, size, size);

            // Unit border
            gc.setStroke(unitColor.darker().darker());
            gc.setLineWidth(1.5);
            gc.strokeOval(ux - halfSize, uy - halfSize, size, size);

            // Unit character emoji/symbol in center
            gc.setFont(troopFont);
            gc.setFill(Color.WHITE);
            String emoji = getUnitEmoji(unit.type);
            double textOffsetX = emoji.length() > 1 ? -9 : -5;
            gc.fillText(emoji, ux + textOffsetX, uy + 5);

            // Unit type label below
            gc.setFont(troopLabelFont);
            gc.setFill(Color.WHITE);
            String shortName = getUnitShortName(unit.type);
            gc.fillText(shortName, ux - shortName.length() * 2.5, uy + halfSize + 10);

            // Health bar above unit
            double healthPct = unit.maxHp > 0 ? (double) unit.hp / unit.maxHp : 1.0;
            if (healthPct < 1.0) {
                double barW = size + 4;
                double barH = 3;
                double barX = ux - barW / 2;
                double barY = uy - halfSize - 6;
                gc.setFill(Color.rgb(60, 0, 0));
                gc.fillRect(barX, barY, barW, barH);
                Color barColor = healthPct > 0.5 ? Color.LIME :
                                 healthPct > 0.25 ? Color.ORANGE : Color.RED;
                gc.setFill(barColor);
                gc.fillRect(barX, barY, barW * healthPct, barH);
            }
        }
    }

    /**
     * Render defense fire projectiles as animated beams/bullets from defense to target.
     */
    private void renderProjectiles(GraphicsContext gc) {
        for (ActiveProjectile p : activeProjectiles) {
            double fromPx = p.fromX * cellWidth;
            double fromPy = p.fromY * cellHeight;
            double toPx = p.toX * cellWidth;
            double toPy = p.toY * cellHeight;

            double progress = 1.0 - (p.remainingTime / PROJECTILE_LIFETIME);
            double alpha = Math.max(0.1, 1.0 - progress * 0.7);

            // Interpolate projectile position along the path
            double projX = fromPx + (toPx - fromPx) * Math.min(progress * 1.5, 1.0);
            double projY = fromPy + (toPy - fromPy) * Math.min(progress * 1.5, 1.0);

            // Projectile color based on defense type
            Color projColor = getProjectileColor(p.defenseType);

            // Draw beam/trail from defense to current projectile position
            gc.setStroke(projColor.deriveColor(0, 1, 1, alpha * 0.5));
            gc.setLineWidth(2);
            gc.strokeLine(fromPx, fromPy, projX, projY);

            // Draw glowing projectile ball at current position
            double size = getProjectileSize(p.defenseType);
            gc.setFill(projColor.deriveColor(0, 1, 1, alpha));
            gc.fillOval(projX - size / 2, projY - size / 2, size, size);

            // Bright core
            gc.setFill(Color.rgb(255, 255, 200, alpha));
            gc.fillOval(projX - size / 4, projY - size / 4, size / 2, size / 2);

            // Impact flash at target when projectile arrives
            if (progress >= 0.6) {
                double impactAlpha = Math.max(0, (1.0 - progress) * 2.5);
                double impactSize = size * 2.5;
                gc.setFill(projColor.deriveColor(0, 0.5, 1.5, impactAlpha * 0.6));
                gc.fillOval(toPx - impactSize / 2, toPy - impactSize / 2, impactSize, impactSize);

                // Damage number
                gc.setFont(troopLabelFont);
                gc.setFill(Color.rgb(255, 50, 50, impactAlpha));
                gc.fillText("-" + p.damage, toPx + 5, toPy - 8);
            }
        }
    }

    /** Get projectile color based on defense type. */
    private Color getProjectileColor(String defenseType) {
        return switch (defenseType) {
            case "CANNON"       -> Color.rgb(80, 80, 80);       // dark gray cannonball
            case "ARCHER_TOWER" -> Color.rgb(150, 80, 200);     // purple arrow
            case "MORTAR"       -> Color.rgb(255, 100, 30);     // orange explosion
            default             -> Color.rgb(255, 200, 50);     // golden default
        };
    }

    /** Get projectile size based on defense type. */
    private double getProjectileSize(String defenseType) {
        return switch (defenseType) {
            case "CANNON"       -> 8;
            case "ARCHER_TOWER" -> 5;
            case "MORTAR"       -> 12;
            default             -> 6;
        };
    }

    // ─── Unit Visual Properties ──────────────────────────────────────

    /** Get the display color for each unit type. */
    private Color getUnitColor(String type) {
        return switch (type) {
            case "BARBARIAN"  -> Color.rgb(220, 160, 60);       // golden-brown
            case "ARCHER"     -> Color.rgb(180, 50, 200);       // purple
            case "GIANT"      -> Color.rgb(160, 100, 50);       // dark brown
            case "GOBLIN"     -> Color.rgb(50, 200, 50);        // bright green
            case "WIZARD"     -> Color.rgb(60, 100, 220);       // royal blue
            case "DRAGON"     -> Color.rgb(200, 50, 30);        // fiery red
            case "HEALER"     -> Color.rgb(255, 180, 200);      // pink
            default           -> Color.YELLOW;
        };
    }

    /** Get the character emoji/symbol for each unit type. */
    private String getUnitEmoji(String type) {
        return switch (type) {
            case "BARBARIAN"  -> "\u2694";  // ⚔ crossed swords
            case "ARCHER"     -> "\u2191";  // ↑ arrow up
            case "GIANT"      -> "\u2660";  // ♠ spade (big guy)
            case "GOBLIN"     -> "\u2663";  // ♣ club
            case "WIZARD"     -> "\u2605";  // ★ star
            case "DRAGON"     -> "\u2666";  // ♦ diamond (fire)
            case "HEALER"     -> "\u2665";  // ♥ heart
            default           -> "?";
        };
    }

    /** Get a short name label for each unit type. */
    private String getUnitShortName(String type) {
        return switch (type) {
            case "BARBARIAN"  -> "BARB";
            case "ARCHER"     -> "ARCH";
            case "GIANT"      -> "GIAN";
            case "GOBLIN"     -> "GOBL";
            case "WIZARD"     -> "WIZ";
            case "DRAGON"     -> "DRAG";
            case "HEALER"     -> "HEAL";
            default           -> type.substring(0, Math.min(4, type.length()));
        };
    }

    /** Get the rendering size (diameter) for each unit type. */
    private double getUnitSize(String type) {
        return switch (type) {
            case "GIANT"   -> 22;
            case "DRAGON"  -> 24;
            case "GOBLIN"  -> 14;
            case "HEALER"  -> 18;
            default        -> 16;  // BARBARIAN, ARCHER, WIZARD
        };
    }

    // ─── Building Visual Properties ──────────────────────────────────

    /** Get the display color for each building type. */
    private Color getBuildingColor(String type) {
        return switch (type) {
            case "TOWN_HALL"        -> Color.GOLDENROD;
            case "GOLD_MINE"        -> Color.GOLD;
            case "ELIXIR_COLLECTOR" -> Color.MEDIUMPURPLE;
            case "GOLD_STORAGE"     -> Color.ORANGE;
            case "ELIXIR_STORAGE"   -> Color.PLUM;
            case "CANNON"           -> Color.DARKGRAY;
            case "ARCHER_TOWER"     -> Color.BROWN;
            case "WALL"             -> Color.SLATEGRAY;
            case "MORTAR"           -> Color.DARKRED;
            case "BARRACKS"         -> Color.SADDLEBROWN;
            case "ARMY_CAMP"        -> Color.OLIVE;
            case "LABORATORY"       -> Color.TEAL;
            default                 -> Color.GRAY;
        };
    }

    /** Get the building label abbreviation. */
    private String getBuildingLabel(String type) {
        return switch (type) {
            case "TOWN_HALL"        -> "TH";
            case "GOLD_MINE"        -> "GM";
            case "ELIXIR_COLLECTOR" -> "EC";
            case "GOLD_STORAGE"     -> "GS";
            case "ELIXIR_STORAGE"   -> "ES";
            case "CANNON"           -> "CA";
            case "ARCHER_TOWER"     -> "AT";
            case "WALL"             -> "W";
            case "MORTAR"           -> "MO";
            case "BARRACKS"         -> "BK";
            case "ARMY_CAMP"        -> "AC";
            case "LABORATORY"       -> "LB";
            default                 -> "??";
        };
    }

    /**
     * Update the Heads-Up Display with current battle stats.
     */
    private void updateHUD() {
        int remaining = Math.max(0, 180 - elapsedSeconds);
        timerLabel.setText("Time: " + UIUtils.formatDuration(remaining));
        destructionLabel.setText(String.format("%.0f%%", destructionPct));
        destructionBar.setProgress(destructionPct / 100.0);

        // Star display
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            stars.append(i < starsEarned ? "\u2605" : "\u2606"); // ★ or ☆
        }
        starsLabel.setText(stars.toString());

        // Animate star label when a new star is earned
        if (starsEarned > previousStars) {
            previousStars = starsEarned;
            ScaleTransition st = new ScaleTransition(Duration.millis(400), starsLabel);
            st.setFromX(1.0);
            st.setFromY(1.0);
            st.setToX(1.6);
            st.setToY(1.6);
            st.setCycleCount(2);
            st.setAutoReverse(true);
            st.play();
            starsLabel.setStyle("-fx-text-fill: gold; -fx-font-size: 24; -fx-effect: dropshadow(gaussian, gold, 12, 0.5, 0, 0);");
        }

        // Battle end detection — stop timer/listeners when time is up or 100% destruction
        if (remaining <= 0 || destructionPct >= 100) {
            cleanupBattle();
        }
    }

    /**
     * Stop animation timer and remove all listeners to prevent resource leaks.
     * Called on battle end (time expired / 100% destruction) and on surrender.
     */
    private void cleanupBattle() {
        if (battleAnimationTimer != null) {
            battleAnimationTimer.stop();
            battleAnimationTimer = null;
        }
        if (elapsedListener != null) {
            gameEngine.battleElapsedProperty().removeListener(elapsedListener);
            elapsedListener = null;
        }
        if (destructionListener != null) {
            gameEngine.battleDestructionProperty().removeListener(destructionListener);
            destructionListener = null;
        }
        if (unitsChangeListener != null) {
            gameEngine.getBattleUnits().removeListener(unitsChangeListener);
            unitsChangeListener = null;
        }
        if (buildingsChangeListener != null) {
            gameEngine.getBattleBuildingStates().removeListener(buildingsChangeListener);
            buildingsChangeListener = null;
        }
        if (fireEventsChangeListener != null) {
            gameEngine.getDefenseFireEvents().removeListener(fireEventsChangeListener);
            fireEventsChangeListener = null;
        }
    }

    // ─── Unit Deployment ─────────────────────────────────────────────

    /**
     * Deploy a unit where the player clicks on the battle canvas.
     * Units can only be deployed at the edges of the map.
     */
    private void onBattleCanvasClicked(MouseEvent event) {
        double x = event.getX() / cellWidth;
        double y = event.getY() / cellHeight;

        // Validate: units can only be deployed at edges
        if (!isEdgePosition(x, y)) {
            unitCountLabel.setText("\u26A0 Deploy at edges only!");
            return;
        }

        if (selectedUnitType == null) {
            unitCountLabel.setText("\u26A0 Select a unit first!");
            return;
        }

        // Check if player has this unit type available
        Player player = gameEngine.getCurrentPlayer();
        if (player != null) {
            boolean hasUnit = player.getArmy().stream()
                    .anyMatch(u -> u.getType().equals(selectedUnitType));
            if (!hasUnit) {
                unitCountLabel.setText("\u26A0 No " + selectedUnitType + " left!");
                return;
            }
            // Remove one unit of this type from local army (server removes from DB)
            player.getArmy().stream()
                    .filter(u -> u.getType().equals(selectedUnitType))
                    .findFirst()
                    .ifPresent(u -> player.getArmy().remove(u));
        }

        gameEngine.deployUnit(selectedUnitType, x, y);
        populateUnitSelector(); // refresh counts
        updateUnitCount();
        needsRedraw = true;
    }

    /**
     * Check if a position is at the edge of the map (valid for deployment).
     */
    private boolean isEdgePosition(double x, double y) {
        return x < 1 || x > 19 || y < 1 || y > 19;
    }

    @FXML
    private void onSurrenderClicked() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Surrender");
        confirm.setHeaderText("Are you sure you want to surrender?");
        confirm.setContentText("You will lose trophies.");
        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                cleanupBattle();
                gameEngine.surrender();
                gameEngine.navigateTo("dashboard");
            }
        });
    }

    /**
     * Update unit count label from player's army.
     */
    private void updateUnitCount() {
        Player player = gameEngine.getCurrentPlayer();
        if (player != null) {
            unitCountLabel.setText("Available: " + player.getArmy().size() + " units");
        }
    }

    /**
     * Populate the unit card tray with per-type cards (card-based, same style as Lab/Train).
     */
    private void populateUnitSelector() {
        unitCardsTray.getChildren().clear();
        Player player = gameEngine.getCurrentPlayer();
        if (player == null) return;

        java.util.Map<String, Long> counts = player.getArmy().stream()
                .collect(java.util.stream.Collectors.groupingBy(Unit::getType, java.util.stream.Collectors.counting()));

        // Build compact troop cards for battle tray
        counts.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String type = entry.getKey();
                    long count = entry.getValue();
                    VBox card = createBattleUnitCard(type, count);
                    unitCardsTray.getChildren().add(card);
                });

        // Auto-select first available if nothing selected
        if (selectedUnitType == null || !counts.containsKey(selectedUnitType)) {
            if (!counts.isEmpty()) {
                selectedUnitType = counts.keySet().iterator().next();
            } else {
                selectedUnitType = null;
            }
        }
        // Highlight selected card
        highlightSelectedBattleCard();
    }

    /**
     * Create a compact troop card for the battle deployment tray.
     */
    private VBox createBattleUnitCard(String troopType, long count) {
        String emoji = TROOP_EMOJIS.getOrDefault(troopType, "⚔");
        String accentColor = TROOP_COLORS.getOrDefault(troopType, "#607d8b");

        VBox card = new VBox(2);
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(70);
        card.setPrefHeight(68);
        card.setPadding(new Insets(4, 4, 4, 4));
        card.setCursor(Cursor.HAND);
        card.setUserData(troopType);

        String bgColor = "rgba(0,40,70,0.85)";
        String borderColor = accentColor;

        card.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 2; "
                + "-fx-border-radius: 8; -fx-background-radius: 8;", bgColor, borderColor));

        Label emojiLabel = new Label(emoji);
        emojiLabel.setStyle("-fx-font-size: 22; -fx-effect: dropshadow(gaussian, " + accentColor + ", 4, 0.3, 0, 0);");

        Label countLabel = new Label("x" + count);
        countLabel.setStyle("-fx-text-fill: #FFD700; -fx-font-size: 12; -fx-font-weight: bold;");

        Label nameLabel = new Label(troopType.substring(0, Math.min(4, troopType.length())));
        nameLabel.setStyle("-fx-text-fill: #b0bec5; -fx-font-size: 8;");

        card.getChildren().addAll(emojiLabel, countLabel, nameLabel);

        // Hover
        card.setOnMouseEntered(e -> {
            if (card != selectedUnitCard) {
                card.setStyle(String.format(
                        "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 2.5; "
                        + "-fx-border-radius: 8; -fx-background-radius: 8; "
                        + "-fx-effect: dropshadow(gaussian, %s, 10, 0.4, 0, 0);",
                        bgColor, borderColor, accentColor));
            }
        });
        card.setOnMouseExited(e -> {
            if (card != selectedUnitCard) {
                card.setStyle(String.format(
                        "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 2; "
                        + "-fx-border-radius: 8; -fx-background-radius: 8;", bgColor, borderColor));
            }
        });

        // Click to select
        card.setOnMouseClicked(e -> {
            selectedUnitType = troopType;
            highlightSelectedBattleCard();
        });

        return card;
    }

    /**
     * Highlight the currently selected card in the battle tray.
     */
    private void highlightSelectedBattleCard() {
        for (javafx.scene.Node node : unitCardsTray.getChildren()) {
            if (node instanceof VBox vbox) {
                String type = (String) vbox.getUserData();
                String accent = TROOP_COLORS.getOrDefault(type, "#607d8b");
                if (type != null && type.equals(selectedUnitType)) {
                    selectedUnitCard = vbox;
                    vbox.setStyle("-fx-background-color: rgba(255,215,0,0.15); -fx-border-color: #FFD700; -fx-border-width: 3; "
                            + "-fx-border-radius: 8; -fx-background-radius: 8; "
                            + "-fx-effect: dropshadow(gaussian, rgba(255,215,0,0.6), 12, 0.5, 0, 0);");
                } else {
                    vbox.setStyle(String.format(
                            "-fx-background-color: rgba(0,40,70,0.85); -fx-border-color: %s; -fx-border-width: 2; "
                            + "-fx-border-radius: 8; -fx-background-radius: 8;", accent));
                }
            }
        }
    }

    /**
     * Called by GameEngine when a BATTLE_UPDATE message is received.
     * Updates local battle state and re-renders.
     */
    public void onBattleUpdate(int elapsed, double destruction, int unitsAlive, int buildingsRemaining) {
        this.elapsedSeconds = elapsed;
        this.destructionPct = destruction;
        this.starsEarned = destruction >= 100 ? 3 : destruction >= 75 ? 2 : destruction >= 50 ? 1 : 0;
        updateHUD();
        needsRedraw = true;
    }

    // ─── Inner Classes ───────────────────────────────────────────────

    /**
     * Represents an active projectile animation from a defense building to a target unit.
     * Has a countdown timer; removed when expired.
     */
    private static class ActiveProjectile {
        final String defenseType;
        final double fromX, fromY;  // grid coords of defense center
        final double toX, toY;      // grid coords of target unit
        final int damage;
        double remainingTime;        // seconds until animation completes

        ActiveProjectile(String defenseType, double fromX, double fromY,
                         double toX, double toY, int damage, double lifetime) {
            this.defenseType = defenseType;
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
            this.damage = damage;
            this.remainingTime = lifetime;
        }
    }
}
