package com.clashgame.client.game;

import com.clashgame.client.model.*;
import com.clashgame.client.network.NetworkClient;
import com.clashgame.client.controller.LeaderboardController;
import com.clashgame.server.model.GameMessage;
import com.clashgame.server.model.GameMessage.MessageType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Core client-side game engine that manages:
 * - Local game state (player, base, army, resources)
 * - Game loop (AnimationTimer for rendering)
 * - Network message processing
 * - Coordination between UI controllers and network layer
 * 
 * Follows the Singleton pattern so all controllers share the same instance.
 * 
 * MVC Role: Part of the Controller layer, bridging Model and View.
 */
public class GameEngine {

    // Singleton instance
    private static GameEngine instance;

    // Game state
    private final ObjectProperty<Player> currentPlayer = new SimpleObjectProperty<>();
    private final BooleanProperty inBattle = new SimpleBooleanProperty(false);
    private final StringProperty currentBattleId = new SimpleStringProperty();
    private final ObservableList<String> chatMessages = FXCollections.observableArrayList();

    // Login error feedback
    private final StringProperty loginError = new SimpleStringProperty();

    // Leaderboard data (Bug 3 fix)
    private final ObservableList<LeaderboardController.LeaderboardEntry> leaderboardData 
            = FXCollections.observableArrayList();

    // Battle state observables (Bug 4 fix)
    private final IntegerProperty battleElapsed = new SimpleIntegerProperty(0);
    private final DoubleProperty battleDestruction = new SimpleDoubleProperty(0);
    private final IntegerProperty battleUnitsAlive = new SimpleIntegerProperty(0);
    private final IntegerProperty battleBuildingsRemaining = new SimpleIntegerProperty(0);

    // Defender base data for battle screen (Bug 21 fix)
    private final ObservableList<Building> defenderBuildings = FXCollections.observableArrayList();

    // Live battle unit positions (for animation rendering)
    private final ObservableList<BattleUnitInfo> battleUnits = FXCollections.observableArrayList();

    // Live battle building states (for health bar rendering)
    private final ObservableList<BattleBuildingInfo> battleBuildingStates = FXCollections.observableArrayList();

    // Defense fire events for projectile/beam animation
    private final ObservableList<DefenseFireInfo> defenseFireEvents = FXCollections.observableArrayList();

    // Counter that increments each time the base is updated from server
    private final IntegerProperty baseUpdateCounter = new SimpleIntegerProperty(0);

    // Training result feedback for TrainController
    private final StringProperty lastTrainResult = new SimpleStringProperty();

    // Lab upgrade result feedback for LaboratoryController
    private final StringProperty lastLabUpgradeResult = new SimpleStringProperty();

    // Troop levels from Laboratory (type -> level)
    private final javafx.collections.ObservableMap<String, Integer> troopLevels =
            FXCollections.observableHashMap();

    // Lab upgrade state
    private final StringProperty labUpgradingTroop = new SimpleStringProperty();
    private final IntegerProperty labUpgradeTimeRemaining = new SimpleIntegerProperty(0);

    // Waiting for base data before navigating (Bug 22 fix)
    private boolean waitingForBaseData = false;

    // Network
    private NetworkClient networkClient;
    private boolean initialized = false;

    // JSON parser
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Matched opponent (from MATCH_FOUND)
    private String matchedOpponentId;

    // Game loop
    private AnimationTimer gameLoop;
    private long lastUpdate;

    // Screen navigation callback
    private Consumer<String> screenNavigator;

    // Training queue data from server
    private final ObservableList<String> trainingQueue = FXCollections.observableArrayList();

    // Battle history data from server
    private final ObservableList<String> battleHistory = FXCollections.observableArrayList();

    // ─── Singleton ───────────────────────────────────────────────────

    private GameEngine() {}

    public static synchronized GameEngine getInstance() {
        if (instance == null) {
            instance = new GameEngine();
        }
        return instance;
    }

    // ─── Initialization ──────────────────────────────────────────────

    /**
     * Initialize the game engine and connect to the server.
     * @param host server hostname
     * @param port server port
     */
    public void initialize(String host, int port) {
        if (initialized && networkClient != null && !networkClient.isConnected()) {
            // Allow re-initialization if not currently connected (e.g. IP changed)
            initialized = false;
        }
        if (initialized) return; // Don't re-create on revisit
        networkClient = new NetworkClient(host, port);
        registerMessageHandlers();
        initialized = true;
    }

    /**
     * Register handlers for all incoming message types.
     */
    private void registerMessageHandlers() {
        networkClient.onMessage(MessageType.LOGIN_SUCCESS, this::handleLoginSuccess);
        networkClient.onMessage(MessageType.LOGIN_FAILED, this::handleLoginFailed);
        networkClient.onMessage(MessageType.UPDATE_BASE, this::handleBaseUpdate);
        networkClient.onMessage(MessageType.RESOURCE_UPDATE, this::handleResourceUpdate);
        networkClient.onMessage(MessageType.UNIT_TRAINED, this::handleUnitTrained);
        networkClient.onMessage(MessageType.UNIT_UPGRADED, this::handleUnitUpgraded);
        networkClient.onMessage(MessageType.MATCH_FOUND, this::handleMatchFound);
        networkClient.onMessage(MessageType.BATTLE_START, this::handleBattleStart);
        networkClient.onMessage(MessageType.BATTLE_UPDATE, this::handleBattleUpdate);
        networkClient.onMessage(MessageType.BATTLE_RESULT, this::handleBattleResult);
        networkClient.onMessage(MessageType.CHAT_MESSAGE, this::handleChatMessage);
        networkClient.onMessage(MessageType.LEADERBOARD, this::handleLeaderboard);
        networkClient.onMessage(MessageType.BATTLE_HISTORY, this::handleBattleHistory);
        networkClient.onMessage(MessageType.ERROR, this::handleError);

        networkClient.onConnectionChanged(connected -> {
            if (!connected) {
                Platform.runLater(() -> navigateTo("login"));
            }
        });
    }

    // ─── Game Loop ───────────────────────────────────────────────────

    /**
     * Start the game rendering loop.
     */
    public void startGameLoop() {
        if (gameLoop != null) return;

        lastUpdate = System.nanoTime();
        gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double deltaTime = (now - lastUpdate) / 1_000_000_000.0;
                lastUpdate = now;
                update(deltaTime);
            }
        };
        gameLoop.start();
    }

    /**
     * Stop the game loop.
     */
    public void stopGameLoop() {
        if (gameLoop != null) {
            gameLoop.stop();
            gameLoop = null;
        }
    }

    /**
     * Update game state each frame.
     * @param deltaTime time since last frame in seconds
     */
    private void update(double deltaTime) {
        if (inBattle.get()) {
            updateBattle(deltaTime);
        }
        // Other per-frame updates (animations, etc.)
    }

    /**
     * Update battle visualization each frame.
     */
    private void updateBattle(double deltaTime) {
        // Animate unit positions based on server updates
        // Update health bars, effects, etc.
    }

    // ─── Authentication ──────────────────────────────────────────────

    /**
     * Connect to server and send login request.
     */
    public void login(String username, String password) throws Exception {
        if (!networkClient.isConnected()) {
            networkClient.connect();
        }
        networkClient.login(username, password);
    }

    /**
     * Register a new account.
     */
    public void register(String username, String password) throws Exception {
        if (!networkClient.isConnected()) {
            networkClient.connect();
        }
        networkClient.register(username, password);
    }

    /**
     * Disconnect from server.
     */
    public void logout() {
        stopGameLoop();
        if (networkClient != null && networkClient.isConnected()) {
            networkClient.disconnect();
        }
        currentPlayer.set(null);
        initialized = false; // Allow re-initialization on reconnect (Bug 25 fix)
    }

    // ─── Game Actions ────────────────────────────────────────────────

    /**
     * Request to place a building on the base.
     */
    public void placeBuilding(String type, int gridX, int gridY) {
        networkClient.placeBuilding(type, gridX, gridY);
    }

    /**
     * Request to upgrade a building.
     */
    public void upgradeBuilding(long buildingId) {
        networkClient.upgradeBuilding(buildingId);
    }

    /**
     * Request to move a building to a new position.
     */
    public void moveBuilding(long buildingId, int gridX, int gridY) {
        networkClient.moveBuilding(buildingId, gridX, gridY);
    }

    /**
     * Request to train units.
     */
    public void trainUnits(String unitType, int count) {
        networkClient.trainUnit(unitType, count);
    }

    /**
     * Request to upgrade a troop type in the Laboratory.
     */
    public void upgradeUnit(String unitType) {
        networkClient.upgradeUnit(unitType);
    }

    /**
     * Request matchmaking to find an opponent.
     */
    public void findOpponent() {
        networkClient.findOpponent();
    }

    /**
     * Initiate an attack against an opponent.
     */
    public void attackPlayer(String defenderId) {
        String armyData = buildArmyJson();
        networkClient.attackPlayer(defenderId, armyData);
    }

    /**
     * Deploy a unit during an active battle.
     */
    public void deployUnit(String unitType, double x, double y) {
        if (currentBattleId.get() != null) {
            networkClient.deployUnit(currentBattleId.get(), unitType, x, y);
        }
    }

    /**
     * Send a chat message.
     */
    public void sendChatMessage(String message) {
        networkClient.sendChat(message);
    }

    /**
     * Surrender from an active battle.
     */
    public void surrender() {
        if (currentBattleId.get() != null) {
            networkClient.sendMessage(new GameMessage(
                MessageType.LEAVE_GAME, networkClient.getPlayerId(), currentBattleId.get()));
            inBattle.set(false);
            currentBattleId.set(null);
        }
    }

    /**
     * Request speed-up of a building upgrade (spends gems).
     */
    public void speedUpBuildingUpgrade(long buildingId) {
        String payload = String.format("{\"buildingId\":%d}", buildingId);
        networkClient.sendMessage(new GameMessage(
                MessageType.SPEED_UP, networkClient.getPlayerId(), payload));
    }

    /**
     * Request speed-up of training queue (spends gems).
     */
    public void speedUpTraining() {
        String payload = "{\"training\":true}";
        networkClient.sendMessage(new GameMessage(
                MessageType.SPEED_UP, networkClient.getPlayerId(), payload));
    }

    /**
     * Request battle history from server.
     */
    public void requestBattleHistory() {
        networkClient.sendMessage(new GameMessage(
                MessageType.BATTLE_HISTORY, networkClient.getPlayerId(), ""));
    }

    // ─── Message Handlers ────────────────────────────────────────────

    private void handleLoginSuccess(GameMessage msg) {
        Player player = new Player();
        player.setId(Long.parseLong(msg.getPayload()));
        currentPlayer.set(player);
        startGameLoop();
        waitingForBaseData = true; // Wait for UPDATE_BASE before navigating (Bug 22 fix)
    }

    private void handleLoginFailed(GameMessage msg) {
        String error = msg.getPayload() != null ? msg.getPayload() : "Invalid credentials";
        Platform.runLater(() -> loginError.set(error));
    }

    private void handleBaseUpdate(GameMessage msg) {
        // Parse full player data JSON and update local state
        try {
            JsonNode root = objectMapper.readTree(msg.getPayload());
            Player player = currentPlayer.get();
            if (player == null) return;

            // Update player fields
            if (root.has("username")) player.setUsername(root.get("username").asText());
            if (root.has("level")) player.setLevel(root.get("level").asInt());
            if (root.has("xp")) player.setExperiencePoints(root.get("xp").asInt());
            if (root.has("gold")) player.setGold(root.get("gold").asInt());
            if (root.has("elixir")) player.setElixir(root.get("elixir").asInt());
            if (root.has("gems")) player.setGems(root.get("gems").asInt());
            if (root.has("trophies")) player.setTrophies(root.get("trophies").asInt());

            // Parse base
            if (root.has("base")) {
                JsonNode baseNode = root.get("base");
                Base base = new Base(baseNode.has("name") ? baseNode.get("name").asText() : "Village");
                if (baseNode.has("id")) base.setId(baseNode.get("id").asLong());

                JsonNode buildings = baseNode.get("buildings");
                if (buildings != null && buildings.isArray()) {
                    for (JsonNode bNode : buildings) {
                        Building building = new Building(bNode.get("type").asText());
                        if (bNode.has("id")) building.setId(bNode.get("id").asLong());
                        if (bNode.has("level")) building.setLevel(bNode.get("level").asInt());
                        // Parse server-authoritative stats (reflect upgrades, etc.)
                        if (bNode.has("width")) building.setWidth(bNode.get("width").asInt());
                        if (bNode.has("height")) building.setHeight(bNode.get("height").asInt());
                        if (bNode.has("hitPoints")) building.setHitPoints(bNode.get("hitPoints").asInt());
                        if (bNode.has("currentHitPoints")) building.setCurrentHitPoints(bNode.get("currentHitPoints").asInt());
                        if (bNode.has("defenseValue")) building.setDefenseValue(bNode.get("defenseValue").asInt());
                        if (bNode.has("productionRate")) building.setProductionRate((int) bNode.get("productionRate").asDouble());
                        if (bNode.has("storageCapacity")) building.setStorageCapacity(bNode.get("storageCapacity").asInt());
                        if (bNode.has("upgrading")) building.setUpgrading(bNode.get("upgrading").asBoolean());
                        if (bNode.has("upgradeTimeRemaining")) building.setUpgradeTimeRemaining(bNode.get("upgradeTimeRemaining").asInt());
                        int gx = bNode.get("gridX").asInt();
                        int gy = bNode.get("gridY").asInt();
                        // Use forced placement — server data is authoritative
                        base.placeBuildingForced(building, gx, gy);
                    }
                }
                player.setBase(base);
            }

            // Parse army
            if (root.has("army")) {
                player.getArmy().clear();
                JsonNode armyNode = root.get("army");
                if (armyNode.isArray()) {
                    for (JsonNode uNode : armyNode) {
                        Unit unit = new Unit(uNode.get("type").asText());
                        if (uNode.has("id")) unit.setId(uNode.get("id").asLong());
                        if (uNode.has("level")) unit.setLevel(uNode.get("level").asInt());
                        player.getArmy().add(unit);
                    }
                }
            }

            // Parse training queue if present
            if (root.has("trainingQueue")) {
                trainingQueue.clear();
                JsonNode queueNode = root.get("trainingQueue");
                if (queueNode.isArray()) {
                    for (JsonNode qNode : queueNode) {
                        String unitType = qNode.get("unitType").asText();
                        int remaining = qNode.get("remaining").asInt();
                        int total = qNode.get("total").asInt();
                        int completed = qNode.get("completed").asInt();
                        trainingQueue.add(String.format("%s: %d/%d (⏱ %ds)",
                                unitType, completed, total, remaining));
                    }
                }
            }

            // Parse troop levels (Laboratory upgrades)
            if (root.has("troopLevels")) {
                JsonNode tlNode = root.get("troopLevels");
                Platform.runLater(() -> {
                    troopLevels.clear();
                    tlNode.fields().forEachRemaining(entry ->
                            troopLevels.put(entry.getKey(), entry.getValue().asInt()));
                });
            }

            // Parse lab upgrade state
            if (root.has("labUpgrade")) {
                JsonNode labNode = root.get("labUpgrade");
                String upgrading = labNode.has("upgrading") && !labNode.get("upgrading").isNull()
                        ? labNode.get("upgrading").asText() : null;
                int timeRemaining = labNode.has("timeRemaining") ? labNode.get("timeRemaining").asInt() : 0;
                Platform.runLater(() -> {
                    labUpgradingTroop.set(upgrading);
                    labUpgradeTimeRemaining.set(timeRemaining);
                });
            }

            // Navigate to dashboard after first base data arrives (Bug 22 fix)
            if (waitingForBaseData) {
                waitingForBaseData = false;
                Platform.runLater(() -> navigateTo("dashboard"));
            }

            // Signal BaseController (and any other listener) to re-render
            baseUpdateCounter.set(baseUpdateCounter.get() + 1);
        } catch (Exception e) {
            System.err.println("Error parsing base update: " + e.getMessage());
        }
    }

    private void handleResourceUpdate(GameMessage msg) {
        Player player = currentPlayer.get();
        if (player == null) return;
        try {
            JsonNode root = objectMapper.readTree(msg.getPayload());
            if (root.has("gold")) player.setGold(root.get("gold").asInt());
            if (root.has("elixir")) player.setElixir(root.get("elixir").asInt());
        } catch (Exception e) {
            System.err.println("Error parsing resource update: " + e.getMessage());
        }
    }

    private void handleUnitTrained(GameMessage msg) {
        // Don't modify army here — the server immediately sends an UPDATE_BASE
        // with the authoritative army list. Adding units here caused double-counting.
        try {
            JsonNode root = objectMapper.readTree(msg.getPayload());
            if (root.has("success") && root.get("success").asBoolean()) {
                String unitType = root.has("type") ? root.get("type").asText() : "?";
                int count = root.has("trained") ? root.get("trained").asInt() : 0;
                System.out.println("Unit trained: " + count + "x " + unitType);
                Platform.runLater(() -> lastTrainResult.set("SUCCESS:" + count + "x " + unitType + " trained!"));
            } else {
                String error = root.has("error") ? root.get("error").asText() : "Training failed";
                System.err.println("Training failed: " + error);
                Platform.runLater(() -> lastTrainResult.set("FAIL:" + error));
            }
        } catch (Exception e) {
            System.err.println("Error parsing unit trained: " + e.getMessage());
            Platform.runLater(() -> lastTrainResult.set("FAIL:Error processing training response"));
        }
    }

    private void handleUnitUpgraded(GameMessage msg) {
        try {
            JsonNode root = objectMapper.readTree(msg.getPayload());
            if (root.has("success") && root.get("success").asBoolean()) {
                String unitType = root.has("type") ? root.get("type").asText() : "?";
                int targetLevel = root.has("targetLevel") ? root.get("targetLevel").asInt() : 0;
                int cost = root.has("cost") ? root.get("cost").asInt() : 0;
                Platform.runLater(() -> lastLabUpgradeResult.set(
                        "SUCCESS:" + unitType + " upgrade to Lv" + targetLevel + " started! (Cost: " + cost + " elixir)"));
            } else {
                String error = root.has("error") ? root.get("error").asText() : "Upgrade failed";
                Platform.runLater(() -> lastLabUpgradeResult.set("FAIL:" + error));
            }
        } catch (Exception e) {
            Platform.runLater(() -> lastLabUpgradeResult.set("FAIL:Error processing upgrade response"));
        }
    }

    private void handleMatchFound(GameMessage msg) {
        matchedOpponentId = msg.getPayload();
        if (matchedOpponentId != null) {
            // Show confirmation dialog before attacking (Bug 9 fix)
            Platform.runLater(() -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Opponent Found");
                confirm.setHeaderText("Match Found!");
                confirm.setContentText("Attack this opponent with your army?");
                confirm.showAndWait().ifPresent(result -> {
                    if (result == javafx.scene.control.ButtonType.OK) {
                        attackPlayer(matchedOpponentId);
                    }
                });
            });
        }
    }

    private void handleBattleStart(GameMessage msg) {
        String payload = msg.getPayload();
        try {
            // Try to parse as JSON with defender base data
            JsonNode root = objectMapper.readTree(payload);
            if (root.has("battleId")) {
                currentBattleId.set(root.get("battleId").asText());
                // Parse defender buildings
                defenderBuildings.clear();
                JsonNode buildingsNode = root.get("buildings");
                if (buildingsNode != null && buildingsNode.isArray()) {
                    for (JsonNode bNode : buildingsNode) {
                        Building b = new Building(bNode.get("type").asText());
                        if (bNode.has("level")) b.setLevel(bNode.get("level").asInt());
                        int gx = bNode.get("gridX").asInt();
                        int gy = bNode.get("gridY").asInt();
                        b.setGridX(gx);
                        b.setGridY(gy);
                        if (bNode.has("width")) b.setWidth(bNode.get("width").asInt());
                        if (bNode.has("height")) b.setHeight(bNode.get("height").asInt());
                        if (bNode.has("hitPoints")) b.setHitPoints(bNode.get("hitPoints").asInt());
                        defenderBuildings.add(b);
                    }
                }
            } else {
                // Simple battleId string (defender side)
                currentBattleId.set(payload);
            }
        } catch (Exception e) {
            // Not JSON, treat as plain battleId
            currentBattleId.set(payload);
        }
        inBattle.set(true);
        Platform.runLater(() -> navigateTo("battle"));
    }

    private void handleBattleUpdate(GameMessage msg) {
        // Parse battle state update and store in observable properties (Bug 4 fix)
        try {
            JsonNode root = objectMapper.readTree(msg.getPayload());
            int elapsed = root.has("elapsed") ? (int) root.get("elapsed").asDouble() : 0;
            double destruction = root.has("destruction") ? root.get("destruction").asDouble() : 0;
            int unitsAlive = root.has("unitsAlive") ? root.get("unitsAlive").asInt() : 0;
            int buildingsRemaining = root.has("buildingsRemaining") ? root.get("buildingsRemaining").asInt() : 0;

            // Parse live unit positions for animation
            List<BattleUnitInfo> units = new ArrayList<>();
            JsonNode unitsNode = root.get("units");
            if (unitsNode != null && unitsNode.isArray()) {
                for (JsonNode uNode : unitsNode) {
                    BattleUnitInfo info = new BattleUnitInfo();
                    info.type = uNode.get("type").asText();
                    info.x = uNode.get("x").asDouble();
                    info.y = uNode.get("y").asDouble();
                    info.hp = uNode.get("hp").asInt();
                    info.maxHp = uNode.get("maxHp").asInt();
                    units.add(info);
                }
            }

            // Parse live building health for damage visualization
            List<BattleBuildingInfo> buildings = new ArrayList<>();
            JsonNode buildingsNode = root.get("buildings");
            if (buildingsNode != null && buildingsNode.isArray()) {
                for (JsonNode bNode : buildingsNode) {
                    BattleBuildingInfo info = new BattleBuildingInfo();
                    info.type = bNode.get("type").asText();
                    info.gridX = bNode.get("gridX").asInt();
                    info.gridY = bNode.get("gridY").asInt();
                    info.width = bNode.get("width").asInt();
                    info.height = bNode.get("height").asInt();
                    info.hp = bNode.get("hp").asInt();
                    info.maxHp = bNode.get("maxHp").asInt();
                    info.destroyed = bNode.get("destroyed").asBoolean();
                    buildings.add(info);
                }
            }

            // Parse defense fire events for projectile animation
            List<DefenseFireInfo> fireEvents = new ArrayList<>();
            JsonNode fireNode = root.get("fireEvents");
            if (fireNode != null && fireNode.isArray()) {
                for (JsonNode fNode : fireNode) {
                    DefenseFireInfo fi = new DefenseFireInfo();
                    fi.defenseType = fNode.get("defType").asText();
                    fi.fromX = fNode.get("fromX").asDouble();
                    fi.fromY = fNode.get("fromY").asDouble();
                    fi.toX = fNode.get("toX").asDouble();
                    fi.toY = fNode.get("toY").asDouble();
                    fi.damage = fNode.get("damage").asInt();
                    fireEvents.add(fi);
                }
            }

            Platform.runLater(() -> {
                battleElapsed.set(elapsed);
                battleDestruction.set(destruction);
                battleUnitsAlive.set(unitsAlive);
                battleBuildingsRemaining.set(buildingsRemaining);
                battleUnits.setAll(units);
                battleBuildingStates.setAll(buildings);
                defenseFireEvents.setAll(fireEvents);
            });
        } catch (Exception e) {
            System.err.println("Error parsing battle update: " + e.getMessage());
        }
    }

    private void handleBattleResult(GameMessage msg) {
        inBattle.set(false);
        currentBattleId.set(null);
        // Parse result for display only; don't modify local resources
        // The server will send a RESOURCE_UPDATE / UPDATE_BASE with correct values (Bug 16 fix)
        try {
            JsonNode root = objectMapper.readTree(msg.getPayload());
            int stars = root.has("stars") ? root.get("stars").asInt() : 0;
            int goldLooted = root.has("goldLooted") ? root.get("goldLooted").asInt() : 0;
            int elixirLooted = root.has("elixirLooted") ? root.get("elixirLooted").asInt() : 0;
            String attacker = root.has("attacker") ? root.get("attacker").asText() : "?";
            String defender = root.has("defender") ? root.get("defender").asText() : "?";

            Platform.runLater(() -> {
                String starDisplay = "\u2605".repeat(stars) + "\u2606".repeat(3 - stars);
                Alert resultAlert = new Alert(Alert.AlertType.INFORMATION);
                resultAlert.setTitle("Battle Complete");
                resultAlert.setHeaderText(starDisplay + "  " + stars + " Star" + (stars != 1 ? "s" : "") + " earned!");
                resultAlert.setContentText(String.format(
                    "%s  vs  %s\n\n"
                    + "\uD83E\uDE99 Gold looted: %d\n"
                    + "\uD83D\uDCA7 Elixir looted: %d",
                    attacker, defender, goldLooted, elixirLooted));
                resultAlert.showAndWait();
                navigateTo("dashboard");
            });
        } catch (Exception e) {
            System.err.println("Error parsing battle result: " + e.getMessage());
            Platform.runLater(() -> navigateTo("dashboard"));
        }
    }

    private void handleChatMessage(GameMessage msg) {
        String chat = msg.getSenderId() + ": " + msg.getPayload();
        chatMessages.add(chat);
    }

    private void handleLeaderboard(GameMessage msg) {
        // Parse leaderboard JSON and populate observable list (Bug 3 fix)
        try {
            JsonNode root = objectMapper.readTree(msg.getPayload());
            List<LeaderboardController.LeaderboardEntry> entries = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    entries.add(new LeaderboardController.LeaderboardEntry(
                        node.get("rank").asInt(),
                        node.get("username").asText(),
                        node.get("level").asInt(),
                        node.get("trophies").asInt()
                    ));
                }
            }
            Platform.runLater(() -> leaderboardData.setAll(entries));
        } catch (Exception e) {
            System.err.println("Error parsing leaderboard: " + e.getMessage());
        }
    }

    private void handleBattleHistory(GameMessage msg) {
        try {
            JsonNode root = objectMapper.readTree(msg.getPayload());
            List<String> entries = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    boolean isAttacker = node.get("isAttacker").asBoolean();
                    String opponent = node.get("opponent").asText();
                    int stars = node.get("stars").asInt();
                    double destruction = node.get("destruction").asDouble();
                    int goldLooted = node.get("goldLooted").asInt();
                    int elixirLooted = node.get("elixirLooted").asInt();
                    int trophiesWon = node.get("trophiesWon").asInt();
                    String status = node.get("status").asText();
                    String starStr = "\u2605".repeat(stars) + "\u2606".repeat(3 - stars);
                    
                    String entry = String.format("%s %s vs %s | %s | %.0f%% | Gold: %d | Elixir: %d | Trophies: %+d | %s",
                            isAttacker ? "\u2694 ATK" : "\uD83D\uDEE1 DEF",
                            starStr, opponent, status, destruction,
                            goldLooted, elixirLooted, trophiesWon, 
                            node.has("date") ? node.get("date").asText() : "");
                    entries.add(entry);
                }
            }
            Platform.runLater(() -> battleHistory.setAll(entries));
        } catch (Exception e) {
            System.err.println("Error parsing battle history: " + e.getMessage());
        }
    }

    private void handleError(GameMessage msg) {
        String error = msg.getPayload() != null ? msg.getPayload() : "An error occurred";
        System.err.println("Server error: " + error);
        if (currentPlayer.get() == null) {
            Platform.runLater(() -> loginError.set(error));
        } else {
            // Show error dialog post-login (Bug 6 fix)
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Server Message");
                alert.setHeaderText(null);
                alert.setContentText(error);
                alert.showAndWait();
            });
        }
    }

    // ─── Utility ─────────────────────────────────────────────────────

    /**
     * Build JSON string from the player's current army.
     */
    private String buildArmyJson() {
        Player player = currentPlayer.get();
        if (player == null) return "{}";

        StringBuilder sb = new StringBuilder("{\"units\":[");
        var army = player.getArmy();
        for (int i = 0; i < army.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"type\":\"%s\",\"level\":%d}",
                    army.get(i).getType(), army.get(i).getLevel()));
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * Navigate to a screen. Calls the registered navigator callback.
     */
    public void navigateTo(String screen) {
        if (screenNavigator != null) {
            if (Platform.isFxApplicationThread()) {
                screenNavigator.accept(screen);
            } else {
                Platform.runLater(() -> screenNavigator.accept(screen));
            }
        }
    }

    // ─── Properties ──────────────────────────────────────────────────

    public Player getCurrentPlayer() { return currentPlayer.get(); }
    public ObjectProperty<Player> currentPlayerProperty() { return currentPlayer; }

    public boolean isInBattle() { return inBattle.get(); }
    public BooleanProperty inBattleProperty() { return inBattle; }

    public ObservableList<String> getChatMessages() { return chatMessages; }

    public void setScreenNavigator(Consumer<String> navigator) {
        this.screenNavigator = navigator;
    }

    public StringProperty loginErrorProperty() { return loginError; }
    public void clearLoginError() { loginError.set(null); }

    public NetworkClient getNetworkClient() { return networkClient; }

    // Leaderboard data for LeaderboardController (Bug 3 fix)
    public ObservableList<LeaderboardController.LeaderboardEntry> getLeaderboardData() { return leaderboardData; }

    // Battle state observables for BattleController (Bug 4 fix)
    public IntegerProperty battleElapsedProperty() { return battleElapsed; }
    public DoubleProperty battleDestructionProperty() { return battleDestruction; }
    public IntegerProperty battleUnitsAliveProperty() { return battleUnitsAlive; }
    public IntegerProperty battleBuildingsRemainingProperty() { return battleBuildingsRemaining; }

    // Defender buildings for battle screen (Bug 21 fix)
    public ObservableList<Building> getDefenderBuildings() { return defenderBuildings; }

    // Live battle unit positions for animation
    public ObservableList<BattleUnitInfo> getBattleUnits() { return battleUnits; }

    // Live battle building states for health visualization
    public ObservableList<BattleBuildingInfo> getBattleBuildingStates() { return battleBuildingStates; }

    // Defense fire events for projectile animation
    public ObservableList<DefenseFireInfo> getDefenseFireEvents() { return defenseFireEvents; }

    // Base update counter — increments each time the server sends an UPDATE_BASE
    public IntegerProperty baseUpdateCounterProperty() { return baseUpdateCounter; }

    // Training result feedback for TrainController
    public StringProperty lastTrainResultProperty() { return lastTrainResult; }

    // Lab upgrade result feedback for LaboratoryController
    public StringProperty lastLabUpgradeResultProperty() { return lastLabUpgradeResult; }

    // Troop levels from Laboratory
    public javafx.collections.ObservableMap<String, Integer> getTroopLevels() { return troopLevels; }

    // Lab upgrade state
    public StringProperty labUpgradingTroopProperty() { return labUpgradingTroop; }
    public IntegerProperty labUpgradeTimeRemainingProperty() { return labUpgradeTimeRemaining; }

    public StringProperty currentBattleIdProperty() { return currentBattleId; }

    public ObservableList<String> getTrainingQueue() { return trainingQueue; }
    public ObservableList<String> getBattleHistory() { return battleHistory; }

    // ─── Inner Data Classes for Battle State ─────────────────────────

    /**
     * Lightweight data class holding a single unit's live battle state.
     * Updated every BATTLE_UPDATE tick from server.
     */
    public static class BattleUnitInfo {
        public String type;
        public double x, y;
        public int hp, maxHp;
    }

    /**
     * Lightweight data class holding a single building's live battle state.
     * Updated every BATTLE_UPDATE tick from server.
     */
    public static class BattleBuildingInfo {
        public String type;
        public int gridX, gridY, width, height;
        public int hp, maxHp;
        public boolean destroyed;
    }

    /**
     * Lightweight data class holding a single defense fire event.
     * Represents a projectile from a defense building to a target unit.
     */
    public static class DefenseFireInfo {
        public String defenseType;
        public double fromX, fromY;  // defense center (grid coords)
        public double toX, toY;      // target unit position (grid coords)
        public int damage;
    }
}
