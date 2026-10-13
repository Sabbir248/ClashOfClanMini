package com.clashgame.server.repository;

import com.clashgame.server.model.Base;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA Repository for Base entities.
 */
@Repository
public interface BaseRepository extends JpaRepository<Base, Long> {

    /**
     * Find a base by its owner's player ID.
     */
    Optional<Base> findByOwnerId(Long playerId);
}
