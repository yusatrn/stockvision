package com.stockvision.watchlist.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link WatchlistJpaEntity}.
 */
public interface SpringDataWatchlistRepository extends JpaRepository<WatchlistJpaEntity, UUID> {

    List<WatchlistJpaEntity> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<WatchlistJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(UUID userId, String name, UUID id);

    void deleteByIdAndUserId(UUID id, UUID userId);
}
