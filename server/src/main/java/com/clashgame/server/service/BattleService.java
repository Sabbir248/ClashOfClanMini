package com.clashgame.server.service;

import com.clashgame.server.game.GameStateManager;
import com.clashgame.server.model.*;
import com.clashgame.server.repository.BattleRepository;
import com.clashgame.server.repository.PlayerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing battle lifecycle:
 * - Initiating battles between players
 * - Processing unit deployments during battles
 * - Storing battle results
 * - Querying battle history
 */
@Service
@Transactional
public class BattleService {

    private static final Logger logger = LoggerFactory.getLogger(BattleService.class);

    @Autowired
    private BattleRepository battleRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private GameStateManager gameStateManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public GameStateManager getGameStateManager() { return gameStateManager; }

    // ─── Battle Initiation ───────────────────────────────────────────

    /**
     * Initiate a battle between attacker and defender.
     * @param attackerId the attacking player's ID
     * @param defenderId the defending player's ID
     * @param armyDataJson JSON: {"units": [{"type": "BARBARIAN", "count": 10}, ...]}
     * @return the battle ID
     */
    public String initiateBattle(String attackerId, String defenderId, String armyDataJson) {
        try {
            Player attacker = playerRepository.findById(Long.parseLong(attackerId)).orElse(null);
            Player defender = playerRepository.findById(Long.parseLong(defenderId)).orElse(null);

            if (attacker == null || defender == null) {
                throw new IllegalArgumentException("Invalid player IDs");
            }

            if (defender.getBase() == null) {
                throw new IllegalStateException("Defender has no base");
            }

            if (defender.getBase().getBuildings() == null || defender.getBase().getBuildings().isEmpty()) {
                throw new IllegalStateException("Defender has no buildings to attack");
            }

            // Check if either player is already in a battle
            if (gameStateManager.isPlayerInBattle(attackerId) ||
                gameStateManager.isPlayerInBattle(defenderId)) {
                throw new IllegalStateException("Player already in battle");
            }

            // Validate attacker has at least 1 unit
            if (attacker.getArmy() == null || attacker.getArmy().isEmpty()) {
                throw new IllegalStateException("Attacker has no army to deploy");
            }

            // Create battle record
            Battle battle = new Battle(attacker, defender);
            battle = battleRepository.save(battle);

            // Start the battle simulation
            String battleId = gameStateManager.startBattle(attacker, defender, battle);

            logger.info("Battle initiated: {} (attacker: {}) vs {} (defender: {})",
                    battleId, attacker.getUsername(), defender.getUsername());

            return battleId;

        } catch (Exception e) {
            logger.error("Error initiating battle", e);
            return null;
        }
    }

    // ─── Unit Deployment ─────────────────────────────────────────────

    /**
     * Deploy a unit during an active battle.
     * @param playerId the deploying player's ID
     * @param deployJson JSON: {"battleId": "...", "unitType": "BARBARIAN", "x": 0.5, "y": 19.5}
     */
    public void deployUnit(String playerId, String deployJson) {
        try {
            JsonNode node = objectMapper.readTree(deployJson);
            String battleId = node.get("battleId").asText();
            UnitType unitType = UnitType.valueOf(node.get("unitType").asText());
            double x = node.get("x").asDouble();
            double y = node.get("y").asDouble();

            // Remove unit from player's army
            Player player = playerRepository.findById(Long.parseLong(playerId)).orElse(null);
            if (player == null) return;

            Unit unitToDeploy = player.getArmy().stream()
                    .filter(u -> u.getType() == unitType && u.isAlive())
                    .findFirst()
                    .orElse(null);

            if (unitToDeploy == null) {
                logger.warn("No available {} unit for player {}", unitType, playerId);
                return;
            }

            player.removeUnit(unitToDeploy);
            playerRepository.save(player); // Persist unit removal (orphanRemoval deletes from DB)
            logger.info("Deployed {} for player {} in battle {}", unitType, playerId, battleId);
            gameStateManager.deployUnitInBattle(battleId, unitToDeploy, x, y);

        } catch (Exception e) {
            logger.error("Error deploying unit", e);
        }
    }

    // ─── Battle Results ──────────────────────────────────────────────

    /**
     * Save battle results after completion.
     */
    public void saveBattleResult(Battle battle) {
        try {
            // Reload fresh entities to avoid detached entity issues
            Player attacker = playerRepository.findById(battle.getAttacker().getId()).orElse(null);
            Player defender = playerRepository.findById(battle.getDefender().getId()).orElse(null);

            if (attacker != null && defender != null) {
                // Apply rewards to fresh entities
                attacker.addGold(battle.getGoldLooted());
                attacker.addElixir(battle.getElixirLooted());
                attacker.adjustTrophies(battle.getTrophiesWon());
                attacker.addExperience(battle.getStarsEarned() * 50);

                defender.spendGold(battle.getGoldLooted());
                defender.spendElixir(battle.getElixirLooted());
                defender.adjustTrophies(-battle.getTrophiesLost());

                // Repair defender's buildings
                if (defender.getBase() != null) {
                    defender.getBase().getBuildings().forEach(Building::repair);
                }

                playerRepository.save(attacker);
                playerRepository.save(defender);
            }

            // Update battle entity references to managed entities for save
            if (attacker != null) battle.setAttacker(attacker);
            if (defender != null) battle.setDefender(defender);
            battleRepository.save(battle);

            logger.info("Battle result saved: {} stars, {}% destruction",
                    battle.getStarsEarned(), String.format("%.1f", battle.getDestructionPercentage()));
        } catch (Exception e) {
            logger.error("Error saving battle result", e);
        }
    }

    // ─── Battle History ──────────────────────────────────────────────

    /**
     * Get battle history for a player (both attacks and defenses).
     * @param playerId the player's ID
     * @return JSON array of battle records
     */
    @Transactional(readOnly = true)
    public String getBattleHistory(String playerId) {
        try {
            Long id = Long.parseLong(playerId);
            Player player = playerRepository.findById(id).orElse(null);
            if (player == null) return "[]";

            List<Battle> allBattles = battleRepository.findByAttackerOrDefenderOrderByCreatedAtDesc(player, player);

            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < allBattles.size(); i++) {
                Battle b = allBattles.get(i);
                if (i > 0) sb.append(",");
                sb.append(String.format(
                        "{\"id\":%d,\"attacker\":\"%s\",\"defender\":\"%s\"," +
                        "\"stars\":%d,\"destruction\":%.1f,\"goldLooted\":%d," +
                        "\"date\":\"%s\",\"isAttacker\":%b}",
                        b.getId(), b.getAttacker().getUsername(), b.getDefender().getUsername(),
                        b.getStarsEarned(), b.getDestructionPercentage(), b.getGoldLooted(),
                        b.getCreatedAt(), b.getAttacker().getId().equals(id)
                ));
            }
            sb.append("]");
            return sb.toString();

        } catch (Exception e) {
            logger.error("Error getting battle history", e);
            return "[]";
        }
    }
}
