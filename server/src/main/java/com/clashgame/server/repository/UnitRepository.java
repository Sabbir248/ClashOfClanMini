package com.clashgame.server.repository;

import com.clashgame.server.model.Unit;
import com.clashgame.server.model.UnitType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA Repository for Unit entities.
 */
@Repository
public interface UnitRepository extends JpaRepository<Unit, Long> {

    /**
     * Find all units belonging to a player.
     */
    List<Unit> findByOwnerId(Long playerId);

    /**
     * Find units of a specific type belonging to a player.
     */
    List<Unit> findByOwnerIdAndType(Long playerId, UnitType type);

    /**
     * Count the number of units a player has.
     */
    int countByOwnerId(Long playerId);
}
