package com.clashgame.server.service;

import com.clashgame.server.model.Player;
import com.clashgame.server.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Matchmaking service that finds suitable opponents for players.
 * 
 * Matchmaking Strategy:
 * 1. Find players within a trophy range (±200)
 * 2. Prefer offline players (so their base is defended by AI)
 * 3. Exclude players currently in battle
 * 4. Exclude players recently attacked by this player
 * 
 * Also supports a matchmaking queue for players seeking PvP matches.
 */
@Service
public class MatchmakingService {

    private static final Logger logger = LoggerFactory.getLogger(MatchmakingService.class);

    // Trophy range for matchmaking (+/- this value)
    private static final int TROPHY_RANGE = 200;

    // Cooldown: don't match the same pair within 5 minutes
    private static final long ATTACK_COOLDOWN_MS = 5 * 60 * 1000;

    @Autowired
    private PlayerRepository playerRepository;

    // Queue for players seeking real-time PvP matches
    private final ConcurrentLinkedQueue<MatchRequest> matchQueue = new ConcurrentLinkedQueue<>();

    // Recent attacks: attackerId -> (defenderId -> timestamp)
    private final Map<String, Map<String, Long>> recentAttacks = new ConcurrentHashMap<>();

    // ─── Find Opponent (Async/Base Attack) ───────────────────────────

    /**
     * Find a suitable opponent for the given player.
     * Searches the database for players within trophy range.
     * @param playerId the searching player's ID
     * @return opponent's player ID, or null if no match found
     */
    @Transactional(readOnly = true)
    public String findOpponent(String playerId) {
        try {
            Player player = playerRepository.findById(Long.parseLong(playerId)).orElse(null);
            if (player == null) return null;

            int minTrophies = Math.max(0, player.getTrophies() - TROPHY_RANGE);
            int maxTrophies = player.getTrophies() + TROPHY_RANGE;

            // Find candidates within trophy range
            List<Player> candidates = playerRepository
                    .findByTrophiesBetweenAndIdNot(minTrophies, maxTrophies, player.getId());

            // Filter out recently attacked players
            candidates.removeIf(c -> isOnCooldown(playerId, c.getId().toString()));

            // Filter out candidates with no base or no buildings (empty bases cause instant battle end)
            candidates.removeIf(c -> c.getBase() == null ||
                    c.getBase().getBuildings() == null ||
                    c.getBase().getBuildings().isEmpty());

            // Shuffle for randomness
            Collections.shuffle(candidates);

            // Prefer offline players (base defense only)
            Optional<Player> offlineOpponent = candidates.stream()
                    .filter(c -> !c.isOnline())
                    .findFirst();

            Player opponent = offlineOpponent.orElseGet(() ->
                    candidates.isEmpty() ? null : candidates.get(0));

            if (opponent != null) {
                recordAttack(playerId, opponent.getId().toString());
                logger.info("Match found: {} vs {}", player.getUsername(), opponent.getUsername());
                return opponent.getId().toString();
            }

            logger.info("No suitable opponent found for player {}", player.getUsername());
            return null;

        } catch (Exception e) {
            logger.error("Error in matchmaking for player {}", playerId, e);
            return null;
        }
    }

    // ─── PvP Queue ───────────────────────────────────────────────────

    /**
     * Add a player to the real-time PvP matchmaking queue.
     */
    public void joinQueue(String playerId, int trophies) {
        matchQueue.add(new MatchRequest(playerId, trophies, System.currentTimeMillis()));
        tryMatchFromQueue();
    }

    /**
     * Remove a player from the matchmaking queue.
     */
    public void leaveQueue(String playerId) {
        matchQueue.removeIf(r -> r.playerId.equals(playerId));
    }

    /**
     * Try to match two players from the queue.
     */
    private synchronized void tryMatchFromQueue() {
        List<MatchRequest> requests = new ArrayList<>(matchQueue);
        if (requests.size() < 2) return;

        // Sort by trophies for closest matching
        requests.sort(Comparator.comparingInt(r -> r.trophies));

        for (int i = 0; i < requests.size() - 1; i++) {
            MatchRequest a = requests.get(i);
            MatchRequest b = requests.get(i + 1);

            if (Math.abs(a.trophies - b.trophies) <= TROPHY_RANGE) {
                matchQueue.remove(a);
                matchQueue.remove(b);
                logger.info("PvP match: {} vs {}", a.playerId, b.playerId);
                // TODO: Notify both players via GameServer
                return;
            }
        }
    }

    // ─── Cooldown Tracking ───────────────────────────────────────────

    /**
     * Record that an attack happened for cooldown tracking.
     */
    private void recordAttack(String attackerId, String defenderId) {
        recentAttacks
                .computeIfAbsent(attackerId, k -> new ConcurrentHashMap<>())
                .put(defenderId, System.currentTimeMillis());
    }

    /**
     * Check if a player pair is still on attack cooldown.
     */
    private boolean isOnCooldown(String attackerId, String defenderId) {
        Map<String, Long> attacks = recentAttacks.get(attackerId);
        if (attacks == null) return false;
        Long lastAttack = attacks.get(defenderId);
        if (lastAttack == null) return false;
        return (System.currentTimeMillis() - lastAttack) < ATTACK_COOLDOWN_MS;
    }

    /**
     * Periodically clean up expired cooldown entries to prevent memory leak.
     */
    @Scheduled(fixedDelay = 600000) // every 10 minutes
    public void cleanupExpiredCooldowns() {
        long now = System.currentTimeMillis();
        recentAttacks.forEach((attackerId, attacks) -> {
            attacks.entrySet().removeIf(e -> (now - e.getValue()) >= ATTACK_COOLDOWN_MS);
        });
        recentAttacks.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    // ─── Inner Classes ───────────────────────────────────────────────

    /**
     * Represents a player's matchmaking request in the PvP queue.
     */
    private static class MatchRequest {
        final String playerId;
        final int trophies;
        final long timestamp;

        MatchRequest(String playerId, int trophies, long timestamp) {
            this.playerId = playerId;
            this.trophies = trophies;
            this.timestamp = timestamp;
        }
    }
}
