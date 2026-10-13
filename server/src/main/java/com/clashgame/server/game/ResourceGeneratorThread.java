package com.clashgame.server.game;

import com.clashgame.server.model.GameMessage;
import com.clashgame.server.model.Player;
import com.clashgame.server.network.GameServer;
import com.clashgame.server.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Spring-managed scheduled service that periodically generates resources
 * for all players. Uses @Scheduled instead of a raw daemon thread to ensure
 * proper Hibernate session management (avoiding LazyInitializationException).
 * 
 * Thread Safety:
 * - Runs within Spring's TaskScheduler thread pool with an active transaction
 * - Database operations are transactional
 */
@Component
public class ResourceGeneratorThread {

    private static final Logger logger = LoggerFactory.getLogger(ResourceGeneratorThread.class);

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    @Lazy
    private GameServer gameServer;

    // ─── Scheduled Resource Generation ───────────────────────────────

    /**
     * Generate resources for all players based on their buildings.
     * Called every 60 seconds by Spring's task scheduler.
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    @Transactional
    public void generateResources() {
        logger.debug("Running resource generation tick...");

        List<Player> allPlayers = playerRepository.findAll();
        int updatedCount = 0;

        for (Player player : allPlayers) {
            if (player.getBase() == null) continue;

            int goldRate = player.getBase().getGoldProductionRate();
            int elixirRate = player.getBase().getElixirProductionRate();

            if (goldRate > 0 || elixirRate > 0) {
                player.addGold(goldRate);
                player.addElixir(elixirRate);
                playerRepository.save(player);
                updatedCount++;

                // Notify online players of resource update
                notifyPlayer(player, goldRate, elixirRate);
            }
        }

        logger.debug("Resource tick complete. Updated {} players.", updatedCount);
    }

    /**
     * Send a resource update message to an online player.
     */
    private void notifyPlayer(Player player, int goldGained, int elixirGained) {
        if (gameServer.isPlayerOnline(player.getId().toString())) {
            String payload = String.format(
                    "{\"gold\":%d,\"elixir\":%d,\"goldGained\":%d,\"elixirGained\":%d}",
                    player.getGold(), player.getElixir(), goldGained, elixirGained
            );
            gameServer.sendToPlayer(player.getId().toString(),
                    GameMessage.resourceUpdate(player.getId().toString(), payload));
        }
    }
}
