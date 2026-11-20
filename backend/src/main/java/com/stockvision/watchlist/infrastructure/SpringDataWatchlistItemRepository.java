package com.stockvision.watchlist.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link WatchlistItemJpaEntity}.
 */
public interface SpringDataWatchlistItemRepository
        extends JpaRepository<WatchlistItemJpaEntity, UUID> {

    List<WatchlistItemJpaEntity> findAllByWatchlistIdInOrderByCreatedAtAsc(
            Collection<UUID> watchlistIds);

    Optional<WatchlistItemJpaEntity> findByIdAndWatchlistId(UUID id, UUID watchlistId);

    boolean existsByWatchlistIdAndInstrumentId(UUID watchlistId, UUID instrumentId);

    void deleteAllByWatchlistId(UUID watchlistId);

    void deleteByIdAndWatchlistId(UUID id, UUID watchlistId);
}
