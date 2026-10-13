package com.clashgame.server.service;

import com.clashgame.server.model.*;
import com.clashgame.server.game.GameStateManager;
import com.clashgame.server.repository.PlayerRepository;
import com.clashgame.server.repository.BaseRepository;
import com.clashgame.server.repository.BuildingRepository;
import com.clashgame.server.repository.UnitRepository;
import com.clashgame.server.repository.BattleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service layer for all player-related business logic.
 * Handles authentication, registration, base management, unit training,
 * and leaderboard queries.
 * 
 * All public methods are transactional to ensure database consistency.
 */
@Service
@Transactional
public class PlayerService {

    private static final Logger logger = LoggerFactory.getLogger(PlayerService.class);

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private BaseRepository baseRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private BattleRepository battleRepository;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private GameStateManager gameStateManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ─── Authentication ──────────────────────────────────────────────

    /**
     * Authenticate a player with username and password.
     * @param credentialsJson JSON: {"username": "...", "password": "..."}
     * @return player ID string if successful, null otherwise
     */
    public String authenticatePlayer(String credentialsJson) {
        try {
            JsonNode node = objectMapper.readTree(credentialsJson);
            String username = node.get("username").asText();
            String password = node.get("password").asText();

            Optional<Player> playerOpt = playerRepository.findByUsername(username);
            if (playerOpt.isPresent()) {
                Player player = playerOpt.get();
                if (passwordEncoder.matches(password, player.getPasswordHash())) {
                    player.setLastLogin(LocalDateTime.now());
                    player.setOnline(true);
                    playerRepository.save(player);
                    logger.info("Player {} authenticated successfully", username);
                    return player.getId().toString();
                }
            }

            logger.warn("Authentication failed for username: {}", username);
            return null;
        } catch (Exception e) {
            logger.error("Error parsing authentication credentials", e);
            return null;
        }
    }

    /**
     * Register a new player account.
     * @param registrationJson JSON: {"username": "...", "password": "..."}
     * @return player ID string if successful, null if username taken
     */
    public String registerPlayer(String registrationJson) {
        try {
            JsonNode node = objectMapper.readTree(registrationJson);
            String username = node.get("username").asText();
            String password = node.get("password").asText();

            // Validate username format
            if (username.length() < 3 || username.length() > 20) {
                logger.warn("Registration failed: username '{}' invalid length", username);
                return null;
            }
            if (!username.matches("[a-zA-Z0-9_]+")) {
                logger.warn("Registration failed: username '{}' invalid characters", username);
                return null;
            }
            if (password.length() < 4) {
                logger.warn("Registration failed: password too short");
                return null;
            }

            // Check for existing username
            if (playerRepository.findByUsername(username).isPresent()) {
                logger.warn("Registration failed: username '{}' already exists", username);
                return null;
            }

            // Create player
            Player player = new Player(username, passwordEncoder.encode(password));
            player = playerRepository.save(player);

            // Create default base with starter buildings
            createDefaultBase(player);

            logger.info("New player registered: {} (ID: {})", username, player.getId());
            return player.getId().toString();

        } catch (Exception e) {
            logger.error("Error during player registration", e);
            return null;
        }
    }

    /**
     * Create a default base for a new player with starter buildings.
     */
    private void createDefaultBase(Player player) {
        Base base = new Base(player, player.getUsername() + "'s Village");
        player.setBase(base);

        // Place Town Hall at center
        Building townHall = new Building(BuildingType.TOWN_HALL);
        base.placeBuilding(townHall, 8, 8);

        // Place one Gold Mine
        Building goldMine = new Building(BuildingType.GOLD_MINE);
        base.placeBuilding(goldMine, 4, 4);

        // Place one Elixir Collector
        Building elixirCollector = new Building(BuildingType.ELIXIR_COLLECTOR);
        base.placeBuilding(elixirCollector, 14, 4);

        // Place one Cannon for basic defense
        Building cannon = new Building(BuildingType.CANNON);
        base.placeBuilding(cannon, 6, 12);

        // Place Barracks for training units
        Building barracks = new Building(BuildingType.BARRACKS);
        base.placeBuilding(barracks, 12, 12);

        // Place Army Camp
        Building armyCamp = new Building(BuildingType.ARMY_CAMP);
        base.placeBuilding(armyCamp, 2, 12);

        baseRepository.save(base);
        playerRepository.save(player);
    }

    // ─── Player Online Status ────────────────────────────────────────

    /**
     * Update a player's online status.
     */
    public void setPlayerOnline(String playerId, boolean online) {
        try {
            Long id = Long.parseLong(playerId);
            playerRepository.findById(id).ifPresent(player -> {
                player.setOnline(online);
                if (!online) {
                    player.setLastLogin(LocalDateTime.now());
                }
                playerRepository.save(player);
            });
        } catch (NumberFormatException e) {
            logger.error("Invalid player ID: {}", playerId);
        }
    }

    // ─── Base Management ─────────────────────────────────────────────

    /**
     * Update a player's base layout from JSON data.
     */
    public void updateBase(String playerId, String baseDataJson) {
        try {
            Long id = Long.parseLong(playerId);
            Player player = playerRepository.findById(id).orElse(null);
            if (player == null || player.getBase() == null) return;

            // Parse and apply base layout changes
            JsonNode node = objectMapper.readTree(baseDataJson);
            JsonNode buildingsNode = node.get("buildings");
            if (buildingsNode != null && buildingsNode.isArray()) {
                Base base = player.getBase();
                for (JsonNode bNode : buildingsNode) {
                    long buildingId = bNode.get("id").asLong();
                    int gridX = bNode.get("gridX").asInt();
                    int gridY = bNode.get("gridY").asInt();
                    // Update building position
                    base.getBuildings().stream()
                            .filter(b -> b.getId() != null && b.getId() == buildingId)
                            .findFirst()
                            .ifPresent(b -> {
                                b.setGridX(gridX);
                                b.setGridY(gridY);
                            });
                }
                playerRepository.save(player);
            }

            logger.debug("Base updated for player {}", playerId);
        } catch (Exception e) {
            logger.error("Error updating base for player {}", playerId, e);
        }
    }

    /**
     * Place a new building on the player's base.
     * @param playerId the player's ID
     * @param buildingJson JSON: {"type": "CANNON", "gridX": 5, "gridY": 3}
     * @return JSON result string
     */
    public String placeBuilding(String playerId, String buildingJson) {
        try {
            Long id = Long.parseLong(playerId);
            Player player = playerRepository.findById(id).orElse(null);
            if (player == null || player.getBase() == null) {
                return "{\"success\":false,\"error\":\"Player or base not found\"}";
            }

            JsonNode node = objectMapper.readTree(buildingJson);
            BuildingType type = BuildingType.valueOf(node.get("type").asText());
            int gridX = node.get("gridX").asInt();
            int gridY = node.get("gridY").asInt();

            // Check building count limit
            long currentCount = player.getBase().getBuildingsByType(type).size();
            if (currentCount >= type.getMaxCount()) {
                return "{\"success\":false,\"error\":\"Max " + type.getDisplayName() + " limit reached (" + type.getMaxCount() + ")\"}";
            }

            // Check if player can afford the building
            int cost = type.getPurchaseCost();
            if (!player.spendGold(cost)) {
                return "{\"success\":false,\"error\":\"Insufficient gold\"}";
            }

            // Place the building
            Building building = new Building(type);
            boolean placed = player.getBase().placeBuilding(building, gridX, gridY);
            if (!placed) {
                player.addGold(cost); // Refund
                return "{\"success\":false,\"error\":\"Invalid placement\"}";
            }

            playerRepository.save(player);
            return "{\"success\":true,\"buildingId\":" + building.getId() + "}";

        } catch (Exception e) {
            logger.error("Error placing building for player {}", playerId, e);
            return "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    /**
     * Start upgrading a building.
     * @param playerId the player's ID
     * @param upgradeJson JSON: {"buildingId": 123}
     * @return JSON result string
     */
    public String upgradeBuilding(String playerId, String upgradeJson) {
        try {
            Long id = Long.parseLong(playerId);
            Player player = playerRepository.findById(id).orElse(null);
            if (player == null) return "{\"success\":false}";

            JsonNode node = objectMapper.readTree(upgradeJson);
            Long buildingId = node.get("buildingId").asLong();

            Building building = buildingRepository.findById(buildingId).orElse(null);
            if (building == null) return "{\"success\":false,\"error\":\"Building not found\"}";

            Base base = player.getBase();
            int thLevel = base.getBaseLevel();  // Town Hall level

            // ─── Upgrade Prerequisites ───────────────────────────────
            if (building.getType() == BuildingType.TOWN_HALL) {
                // Town Hall upgrade: ALL other buildings must be at least current TH level
                int requiredLevel = building.getLevel();
                for (Building b : base.getBuildings()) {
                    if (b.getType() != BuildingType.TOWN_HALL && b.getType() != BuildingType.WALL
                            && b.getLevel() < requiredLevel) {
                        return "{\"success\":false,\"error\":\"Upgrade all buildings to level "
                                + requiredLevel + " before upgrading Town Hall\"}";
                    }
                }
            } else {
                // Non-TH buildings: cannot exceed Town Hall level
                if (building.getLevel() >= thLevel) {
                    return "{\"success\":false,\"error\":\"Upgrade Town Hall to level "
                            + (thLevel + 1) + " first (current TH: " + thLevel + ")\"}";
                }
            }

            int cost = building.startUpgrade(thLevel * 3);
            if (cost < 0) return "{\"success\":false,\"error\":\"Cannot upgrade\"}";
            if (!player.spendGold(cost)) {
                // Revert the upgrading flag since player can't afford it
                building.setUpgrading(false);
                return "{\"success\":false,\"error\":\"Insufficient gold\"}";
            }

            // Building is now in "upgrading" state with a timer.
            // The GameStateManager.gameTick() will decrement the timer each second.
            // When timer reaches 0, completeUpgrade() will be called automatically.
            // Player can spend gems to skip the timer via speedUpUpgrade().

            buildingRepository.save(building);
            playerRepository.save(player);
            return String.format("{\"success\":true,\"cost\":%d,\"upgradeTime\":%d}",
                    cost, building.getUpgradeTimeRemaining());

        } catch (Exception e) {
            logger.error("Error upgrading building", e);
            return "{\"success\":false}";
        }
    }

    // ─── Unit Training ───────────────────────────────────────────────

    /**
     * Process one tick (1 second) of building upgrade timers for all players.
     * Called by GameStateManager every second.
     * 
     * @return list of [playerId, buildingId, newLevel] for completed upgrades
     */
    public java.util.List<long[]> processUpgradeTicks() {
        java.util.List<long[]> completedUpgrades = new java.util.ArrayList<>();
        
        // Find all buildings currently upgrading
        java.util.List<Building> upgradingBuildings = buildingRepository.findByUpgradingTrue();
        
        for (Building building : upgradingBuildings) {
            int remaining = building.getUpgradeTimeRemaining();
            if (remaining <= 1) {
                // Upgrade complete!
                building.completeUpgrade();
                buildingRepository.save(building);
                
                // Find the player who owns this building
                Long playerId = building.getBase().getOwner().getId();
                completedUpgrades.add(new long[]{playerId, building.getId(), building.getLevel()});
                
                logger.info("Building {} upgraded to level {} for player {}",
                        building.getType(), building.getLevel(), playerId);
            } else {
                // Decrement timer
                building.setUpgradeTimeRemaining(remaining - 1);
                buildingRepository.save(building);
            }
        }
        
        return completedUpgrades;
    }

    /**
     * Speed up a building upgrade by spending gems.
     * Cost: 1 gem per 10 seconds remaining (minimum 1 gem).
     * 
     * @param playerId the player's ID
     * @param speedUpJson JSON: {"buildingId": 123}
     * @return JSON result string
     */
    public String speedUpUpgrade(String playerId, String speedUpJson) {
        try {
            Long id = Long.parseLong(playerId);
            Player player = playerRepository.findById(id).orElse(null);
            if (player == null) return "{\"success\":false,\"error\":\"Player not found\"}";

            JsonNode node = objectMapper.readTree(speedUpJson);
            Long buildingId = node.get("buildingId").asLong();

            Building building = buildingRepository.findById(buildingId).orElse(null);
            if (building == null) return "{\"success\":false,\"error\":\"Building not found\"}";
            if (!building.isUpgrading()) {
                return "{\"success\":false,\"error\":\"Building is not upgrading\"}";
            }

            // Calculate gem cost: 1 gem per 10 seconds remaining (min 1)
            int remaining = building.getUpgradeTimeRemaining();
            int gemCost = Math.max(1, (remaining + 9) / 10);

            if (player.getGems() < gemCost) {
                return "{\"success\":false,\"error\":\"Not enough gems! Need " + gemCost + ", have " + player.getGems() + "\"}";
            }

            // Spend gems and complete upgrade instantly
            player.setGems(player.getGems() - gemCost);
            building.completeUpgrade();
            
            buildingRepository.save(building);
            playerRepository.save(player);
            
            logger.info("Player {} spent {} gems to speed up {} to level {}",
                    playerId, gemCost, building.getType(), building.getLevel());
            
            return String.format("{\"success\":true,\"gemsSpent\":%d,\"newLevel\":%d}",
                    gemCost, building.getLevel());

        } catch (Exception e) {
            logger.error("Error speeding up upgrade for player {}", playerId, e);
            return "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    // ─── Training Queue ──────────────────────────────────────────────

    /**
     * Train a new unit for the player.
     * @param playerId the player's ID
     * @param trainJson JSON: {"unitType": "BARBARIAN", "count": 5}
     * @return JSON result string
     */
    public String trainUnit(String playerId, String trainJson) {
        try {
            Long id = Long.parseLong(playerId);
            Player player = playerRepository.findById(id).orElse(null);
            if (player == null) return "{\"success\":false}";

            JsonNode node = objectMapper.readTree(trainJson);
            UnitType unitType = UnitType.valueOf(node.get("unitType").asText());
            int count = node.has("count") ? Math.min(node.get("count").asInt(), 50) : 1;
            if (count <= 0) return "{\"success\":false,\"error\":\"Invalid count\"}";

            // ─── Barracks Level Requirement ──────────────────────────
            // Higher barracks level unlocks stronger troops:
            // Lv1: Barbarian, Archer, Goblin
            // Lv2: + Giant, Wizard
            // Lv3: + Healer
            // Lv4: + Dragon
            int maxBarracksLevel = player.getBase().getBuildingsByType(BuildingType.BARRACKS).stream()
                    .mapToInt(Building::getLevel).max().orElse(0);
            if (maxBarracksLevel == 0) {
                return "{\"success\":false,\"error\":\"No Barracks built\"}";
            }
            int requiredBarracksLevel = getRequiredBarracksLevel(unitType);
            if (maxBarracksLevel < requiredBarracksLevel) {
                return "{\"success\":false,\"error\":\"Barracks level "
                        + requiredBarracksLevel + " required for " + unitType.getDisplayName()
                        + " (current: " + maxBarracksLevel + ")\"}";
            }

            // Calculate total cost
            int costPerUnit = unitType.getTrainingCostElixir();
            int totalCost = costPerUnit * count;

            if (!player.spendElixir(totalCost)) {
                return "{\"success\":false,\"error\":\"Insufficient elixir\"}";
            }

            // Check army camp capacity
            int currentSpace = player.getArmy().stream().mapToInt(Unit::getHousingSpace).sum();
            int maxSpace = player.getBase().getBuildingsByType(BuildingType.ARMY_CAMP).stream()
                    .mapToInt(Building::getStorageCapacity).sum();
            int requiredSpace = unitType.getHousingSpace() * count;

            if (currentSpace + requiredSpace > maxSpace) {
                player.addElixir(totalCost); // Refund
                return "{\"success\":false,\"error\":\"Army camp full\"}";
            }

            // Add to training queue instead of instant creation
            // Units will be created one-by-one as the timer ticks down
            int trainingTime = unitType.getTrainingTimeSeconds();
            gameStateManager.addToTrainingQueue(playerId, unitType.name(), trainingTime, count);

            playerRepository.save(player);
            return String.format("{\"success\":true,\"queued\":%d,\"type\":\"%s\",\"cost\":%d,\"trainTime\":%d}",
                    count, unitType.name(), totalCost, trainingTime);

        } catch (Exception e) {
            logger.error("Error training units for player {}", playerId, e);
            return "{\"success\":false}";
        }
    }

    // ─── Barracks Troop Unlock Requirements ─────────────────────────

    /**
     * Get the minimum barracks level required to train a given unit type.
     * Lv1: Barbarian, Archer, Goblin
     * Lv2: + Giant, Wizard
     * Lv3: + Healer
     * Lv4: + Dragon
     */
    private int getRequiredBarracksLevel(UnitType unitType) {
        return switch (unitType) {
            case BARBARIAN, ARCHER, GOBLIN -> 1;
            case GIANT, WIZARD             -> 2;
            case HEALER                    -> 3;
            case DRAGON                    -> 4;
        };
    }

    // ─── Laboratory Troop Upgrade ────────────────────────────────────

    /**
     * Upgrade a troop type's level in the Laboratory.
     * @param playerId the player's ID
     * @param upgradeJson JSON: {"unitType": "BARBARIAN"}
     * @return JSON result string
     */
    public String upgradeUnit(String playerId, String upgradeJson) {
        try {
            Long id = Long.parseLong(playerId);
            Player player = playerRepository.findById(id).orElse(null);
            if (player == null) return "{\"success\":false,\"error\":\"Player not found\"}";

            JsonNode node = objectMapper.readTree(upgradeJson);
            String unitTypeName = node.get("unitType").asText();
            UnitType unitType = UnitType.valueOf(unitTypeName);

            // Check if player has a Laboratory
            int labLevel = player.getBase().getBuildingsByType(BuildingType.LABORATORY).stream()
                    .mapToInt(Building::getLevel).max().orElse(0);
            if (labLevel == 0) {
                return "{\"success\":false,\"error\":\"No Laboratory built! Build a Laboratory first.\"}";
            }

            // Check if laboratory is currently upgrading a building
            boolean labUpgrading = player.getBase().getBuildingsByType(BuildingType.LABORATORY).stream()
                    .anyMatch(Building::isUpgrading);
            if (labUpgrading) {
                return "{\"success\":false,\"error\":\"Laboratory is being upgraded.\"}";
            }

            // Check if already upgrading a troop
            if (player.getLabUpgradingTroop() != null) {
                return "{\"success\":false,\"error\":\"Laboratory is busy upgrading "
                        + player.getLabUpgradingTroop().getDisplayName()
                        + "! Wait for it to finish.\"}";
            }

            // Current and target levels
            int currentLevel = player.getTroopLevel(unitType);
            int targetLevel = currentLevel + 1;

            // Check max level
            if (currentLevel >= unitType.getMaxLevel()) {
                return "{\"success\":false,\"error\":\""
                        + unitType.getDisplayName() + " is already at max level ("
                        + unitType.getMaxLevel() + ")!\"}";
            }

            // Check lab level requirement
            int requiredLabLevel = unitType.getRequiredLabLevelForUpgrade(targetLevel);
            if (labLevel < requiredLabLevel) {
                return "{\"success\":false,\"error\":\"Laboratory level "
                        + requiredLabLevel + " required to upgrade "
                        + unitType.getDisplayName() + " to level " + targetLevel
                        + " (current Lab: " + labLevel + ")\"}";
            }

            // Check elixir cost
            int cost = unitType.getUpgradeCost(targetLevel);
            if (!player.spendElixir(cost)) {
                return "{\"success\":false,\"error\":\"Not enough elixir! Need "
                        + cost + ", have " + player.getElixir() + "\"}";
            }

            // Start the upgrade timer
            int upgradeTime = unitType.getUpgradeTime(targetLevel);
            player.setLabUpgradingTroop(unitType);
            player.setLabUpgradeTimeRemaining(upgradeTime);

            playerRepository.save(player);

            logger.info("Player {} started upgrading {} to level {} (cost: {}, time: {}s)",
                    playerId, unitType.getDisplayName(), targetLevel, cost, upgradeTime);

            return String.format(
                    "{\"success\":true,\"type\":\"%s\",\"targetLevel\":%d,\"cost\":%d,\"upgradeTime\":%d}",
                    unitType.name(), targetLevel, cost, upgradeTime);

        } catch (Exception e) {
            logger.error("Error upgrading unit for player {}", playerId, e);
            return "{\"success\":false,\"error\":\"Upgrade failed\"}";
        }
    }

    /**
     * Process one tick (1 second) of laboratory upgrade timers for all players.
     * Called by GameStateManager every second.
     *
     * @return list of [playerId] for completed lab upgrades
     */
    public java.util.List<Long> processLabUpgradeTicks() {
        java.util.List<Long> completed = new java.util.ArrayList<>();

        // Find all players with active lab upgrades
        java.util.List<Player> upgrading = playerRepository.findByLabUpgradingTroopIsNotNull();

        for (Player player : upgrading) {
            int remaining = player.getLabUpgradeTimeRemaining();
            if (remaining <= 1) {
                // Upgrade complete!
                UnitType type = player.getLabUpgradingTroop();
                int newLevel = player.getTroopLevel(type) + 1;
                player.setTroopLevel(type, newLevel);
                player.setLabUpgradingTroop(null);
                player.setLabUpgradeTimeRemaining(0);

                // Update all existing army units of this type to the new level
                for (Unit unit : player.getArmy()) {
                    if (unit.getType() == type) {
                        unit.setLevel(newLevel);
                        unit.scaleStatsToLevel();
                    }
                }

                playerRepository.save(player);
                completed.add(player.getId());

                logger.info("Lab upgrade complete: {} {} to level {} for player {}",
                        type.getDisplayName(), type.name(), newLevel, player.getId());
            } else {
                player.setLabUpgradeTimeRemaining(remaining - 1);
                playerRepository.save(player);
            }
        }

        return completed;
    }

    /**
     * Create a single trained unit and add it to the player's army.
     * Called by GameStateManager when a training queue item completes.
     */
    @Transactional
    public void createTrainedUnit(String playerId, String unitTypeName) {
        Player player = playerRepository.findById(Long.parseLong(playerId)).orElse(null);
        if (player == null) return;
        
        UnitType unitType = UnitType.valueOf(unitTypeName);
        int troopLevel = player.getTroopLevel(unitType);
        Unit unit = new Unit(unitType, troopLevel);
        player.addUnit(unit);
        playerRepository.save(player);
        
        logger.debug("Created trained unit {} (Lv{}) for player {}", unitTypeName, troopLevel, playerId);
    }

    // ─── Building Move ───────────────────────────────────────────────

    /**
     * Move (relocate) a building to a new grid position.
     * @param playerId the player's ID
     * @param moveJson JSON: {"buildingId": 123, "gridX": 5, "gridY": 3}
     * @return JSON result string
     */
    public String moveBuilding(String playerId, String moveJson) {
        try {
            Long id = Long.parseLong(playerId);
            Player player = playerRepository.findById(id).orElse(null);
            if (player == null || player.getBase() == null) {
                return "{\"success\":false,\"error\":\"Player or base not found\"}";
            }

            JsonNode node = objectMapper.readTree(moveJson);
            Long buildingId = node.get("buildingId").asLong();
            int newGridX = node.get("gridX").asInt();
            int newGridY = node.get("gridY").asInt();

            Building building = buildingRepository.findById(buildingId).orElse(null);
            if (building == null) return "{\"success\":false,\"error\":\"Building not found\"}";

            Base base = player.getBase();

            // Validate new position: within grid bounds
            if (newGridX < 0 || newGridY < 0 ||
                newGridX + building.getWidth() > base.getGridWidth() ||
                newGridY + building.getHeight() > base.getGridHeight()) {
                return "{\"success\":false,\"error\":\"Position out of bounds\"}";
            }

            // Check for overlap with OTHER buildings (exclude self)
            for (Building existing : base.getBuildings()) {
                if (existing.getId().equals(buildingId)) continue;
                if (newGridX < existing.getGridX() + existing.getWidth() &&
                    newGridX + building.getWidth() > existing.getGridX() &&
                    newGridY < existing.getGridY() + existing.getHeight() &&
                    newGridY + building.getHeight() > existing.getGridY()) {
                    return "{\"success\":false,\"error\":\"Position overlaps another building\"}";
                }
            }

            building.setGridX(newGridX);
            building.setGridY(newGridY);
            buildingRepository.save(building);
            playerRepository.save(player);
            return "{\"success\":true}";

        } catch (Exception e) {
            logger.error("Error moving building for player {}", playerId, e);
            return "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    // ─── Leaderboard ─────────────────────────────────────────────────

    /**
     * Get the top players by trophies (leaderboard).
     * @return JSON array of top players
     */
    @Transactional(readOnly = true)
    public String getLeaderboard() {
        List<Player> topPlayers = playerRepository.findTop20ByOrderByTrophiesDesc();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < topPlayers.size(); i++) {
            Player p = topPlayers.get(i);
            if (i > 0) sb.append(",");
            String safeName = p.getUsername().replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append(String.format(
                    "{\"rank\":%d,\"username\":\"%s\",\"level\":%d,\"trophies\":%d}",
                    i + 1, safeName, p.getLevel(), p.getTrophies()));
        }
        sb.append("]");
        return sb.toString();
    }

    // ─── Lookup ──────────────────────────────────────────────────────

    /**
     * Get full player data as JSON (sent after login to populate client state).
     */
    @Transactional(readOnly = true)
    public String getPlayerDataJson(String playerId) {
        try {
            Long id = Long.parseLong(playerId);
            Player player = playerRepository.findById(id).orElse(null);
            if (player == null) return null;

            StringBuilder sb = new StringBuilder("{");
            String safeUsername = player.getUsername().replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append(String.format("\"id\":%d,\"username\":\"%s\",\"level\":%d,\"xp\":%d,",
                    player.getId(), safeUsername, player.getLevel(), player.getExperiencePoints()));
            sb.append(String.format("\"gold\":%d,\"elixir\":%d,\"gems\":%d,\"trophies\":%d,",
                    player.getGold(), player.getElixir(), player.getGems(), player.getTrophies()));

            // Base data
            Base base = player.getBase();
            if (base != null) {
                sb.append("\"base\":{");
                sb.append(String.format("\"id\":%d,\"name\":\"%s\",\"gridWidth\":%d,\"gridHeight\":%d,\"buildings\":[",
                        base.getId(), base.getBaseName(), base.getGridWidth(), base.getGridHeight()));
                List<Building> buildings = base.getBuildings();
                for (int i = 0; i < buildings.size(); i++) {
                    Building b = buildings.get(i);
                    if (i > 0) sb.append(",");
                    sb.append(String.format("{\"id\":%d,\"type\":\"%s\",\"level\":%d,\"gridX\":%d,\"gridY\":%d,"
                                    + "\"width\":%d,\"height\":%d,\"hitPoints\":%d,\"currentHitPoints\":%d,"
                                    + "\"defenseValue\":%d,\"productionRate\":%.1f,\"storageCapacity\":%d,"
                                    + "\"upgrading\":%b,\"upgradeTimeRemaining\":%d}",
                            b.getId(), b.getType(), b.getLevel(), b.getGridX(), b.getGridY(),
                            b.getWidth(), b.getHeight(), b.getHitPoints(), b.getCurrentHitPoints(),
                            b.getDefenseValue(), b.getProductionRate(), b.getStorageCapacity(),
                            b.isUpgrading(), b.getUpgradeTimeRemaining()));
                }
                sb.append("]},");
            }

            // Army data
            sb.append("\"army\":[");
            List<Unit> army = player.getArmy();
            for (int i = 0; i < army.size(); i++) {
                Unit u = army.get(i);
                if (i > 0) sb.append(",");
                sb.append(String.format("{\"id\":%d,\"type\":\"%s\",\"level\":%d,\"hitPoints\":%d,"
                                + "\"attackPower\":%d,\"speed\":%.1f}",
                        u.getId(), u.getType(), u.getLevel(), u.getHitPoints(),
                        u.getAttackPower(), u.getSpeed()));
            }
            sb.append("],");

            // Training queue data
            sb.append("\"trainingQueue\":");
            sb.append(gameStateManager.getTrainingQueueJson(playerId));
            sb.append(",");

            // Troop levels data (Laboratory upgrades)
            sb.append("\"troopLevels\":{");
            var troopLevels = player.getTroopLevels();
            boolean firstTroop = true;
            for (UnitType ut : UnitType.values()) {
                if (!firstTroop) sb.append(",");
                sb.append(String.format("\"%s\":%d", ut.name(), player.getTroopLevel(ut)));
                firstTroop = false;
            }
            sb.append("},");

            // Lab upgrade state
            sb.append("\"labUpgrade\":{");
            if (player.getLabUpgradingTroop() != null) {
                sb.append(String.format("\"upgrading\":\"%s\",\"timeRemaining\":%d",
                        player.getLabUpgradingTroop().name(), player.getLabUpgradeTimeRemaining()));
            } else {
                sb.append("\"upgrading\":null,\"timeRemaining\":0");
            }
            sb.append("}");

            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            logger.error("Error building player data JSON for {}", playerId, e);
            return null;
        }
    }

    /**
     * Remove a building from the player's base.
     * @param playerId the player's ID
     * @param removeJson JSON: {"buildingId": 123}
     * @return JSON result string
     */
    public String removeBuilding(String playerId, String removeJson) {
        try {
            Long id = Long.parseLong(playerId);
            Player player = playerRepository.findById(id).orElse(null);
            if (player == null || player.getBase() == null) {
                return "{\"success\":false,\"error\":\"Player or base not found\"}";
            }

            JsonNode node = objectMapper.readTree(removeJson);
            Long buildingId = node.get("buildingId").asLong();

            Building building = buildingRepository.findById(buildingId).orElse(null);
            if (building == null) return "{\"success\":false,\"error\":\"Building not found\"}";

            // Don't allow removing the Town Hall
            if (building.getType() == BuildingType.TOWN_HALL) {
                return "{\"success\":false,\"error\":\"Cannot remove Town Hall\"}";
            }

            // Refund half the purchase cost
            int refund = building.getType().getPurchaseCost() / 2;
            player.addGold(refund);

            player.getBase().removeBuilding(building);
            buildingRepository.delete(building);
            playerRepository.save(player);

            return "{\"success\":true,\"refund\":" + refund + "}";
        } catch (Exception e) {
            logger.error("Error removing building for player {}", playerId, e);
            return "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    /**
     * Get a player by ID.
     */
    @Transactional(readOnly = true)
    public Player getPlayerById(String playerId) {
        try {
            return playerRepository.findById(Long.parseLong(playerId)).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Get a player by username.
     */
    @Transactional(readOnly = true)
    public Player getPlayerByUsername(String username) {
        return playerRepository.findByUsername(username).orElse(null);
    }

    // ─── Battle History ──────────────────────────────────────────────

    /**
     * Get the battle history for a player as a JSON array.
     * Returns the 20 most recent completed battles where the player was
     * either attacker or defender.
     * 
     * @param playerId the player's ID
     * @return JSON array string of battle history entries
     */
    @Transactional(readOnly = true)
    public String getBattleHistory(String playerId) {
        try {
            Long id = Long.parseLong(playerId);
            Player player = playerRepository.findById(id).orElse(null);
            if (player == null) return "[]";

            List<Battle> battles = battleRepository
                    .findByAttackerOrDefenderOrderByCreatedAtDesc(player, player);

            // Limit to 20 most recent
            if (battles.size() > 20) battles = battles.subList(0, 20);

            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < battles.size(); i++) {
                Battle b = battles.get(i);
                if (i > 0) sb.append(",");
                
                boolean isAttacker = b.getAttacker().getId().equals(id);
                String opponentName = isAttacker 
                        ? b.getDefender().getUsername() 
                        : b.getAttacker().getUsername();
                String safeOpponent = opponentName.replace("\\", "\\\\").replace("\"", "\\\"");
                
                sb.append(String.format(
                        "{\"id\":%d,\"opponent\":\"%s\",\"isAttacker\":%b,"
                        + "\"stars\":%d,\"destruction\":%.1f,"
                        + "\"goldLooted\":%d,\"elixirLooted\":%d,"
                        + "\"trophiesWon\":%d,\"trophiesLost\":%d,"
                        + "\"status\":\"%s\",\"date\":\"%s\"}",
                        b.getId(), safeOpponent, isAttacker,
                        b.getStarsEarned(), b.getDestructionPercentage(),
                        b.getGoldLooted(), b.getElixirLooted(),
                        b.getTrophiesWon(), b.getTrophiesLost(),
                        b.getStatus(),
                        b.getCreatedAt() != null ? b.getCreatedAt().toString() : ""));
            }
            sb.append("]");
            return sb.toString();

        } catch (Exception e) {
            logger.error("Error getting battle history for player {}", playerId, e);
            return "[]";
        }
    }
}
