package com.clashgame.server.repository;

import com.clashgame.server.model.Building;
import com.clashgame.server.model.BuildingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA Repository for Building entities.
 */
@Repository
public interface BuildingRepository extends JpaRepository<Building, Long> {

    /**
     * Find all buildings belonging to a base.
     */
    List<Building> findByBaseId(Long baseId);

    /**
     * Find buildings of a specific type in a base.
     */
    List<Building> findByBaseIdAndType(Long baseId, BuildingType type);

    /**
     * Find all buildings currently being upgraded.
     */
    List<Building> findByUpgradingTrue();
}
