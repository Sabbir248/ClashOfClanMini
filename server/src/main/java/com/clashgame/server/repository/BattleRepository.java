package com.clashgame.server.repository;

import com.clashgame.server.model.Battle;
import com.clashgame.server.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA Repository for Battle entities.
 */
@Repository
public interface BattleRepository extends JpaRepository<Battle, Long> {

    /**
     * Find battles where the player was attacker or defender, most recent first.
     */
    List<Battle> findByAttackerOrDefenderOrderByCreatedAtDesc(Player attacker, Player defender);

    /**
     * Find battles by attacker ID.
     */
    List<Battle> findByAttackerId(Long attackerId);

    /**
     * Find battles by defender ID.
     */
    List<Battle> findByDefenderId(Long defenderId);

    /**
     * Find active (in-progress) battles.
     */
    List<Battle> findByStatus(Battle.BattleStatus status);
}
