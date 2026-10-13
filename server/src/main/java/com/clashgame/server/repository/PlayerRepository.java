package com.clashgame.server.repository;

import com.clashgame.server.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for Player entities.
 * Provides CRUD operations and custom query methods.
 */
@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    /**
     * Find a player by username (for authentication).
     */
    Optional<Player> findByUsername(String username);

    /**
     * Check if a username is already taken.
     */
    boolean existsByUsername(String username);

    /**
     * Find players within a trophy range, excluding a specific player.
     * Used for matchmaking.
     */
    List<Player> findByTrophiesBetweenAndIdNot(int minTrophies, int maxTrophies, Long excludeId);

    /**
     * Find all currently online players.
     */
    List<Player> findByOnlineTrue();

    /**
     * Get top 20 players by trophies (leaderboard).
     */
    List<Player> findTop20ByOrderByTrophiesDesc();

    /**
     * Find players belonging to a clan.
     */
    List<Player> findByClanName(String clanName);

    /**
     * Find all players with an active laboratory troop upgrade.
     */
    List<Player> findByLabUpgradingTroopIsNotNull();
}
