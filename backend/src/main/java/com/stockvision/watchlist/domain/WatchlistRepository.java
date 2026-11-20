package com.stockvision.watchlist.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for user watchlists and instrument items.
 */
public interface WatchlistRepository {

    List<Watchlist> findAllByUserId(UUID userId);

    Optional<Watchlist> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(UUID userId, String name, UUID excludedId);

    Watchlist save(Watchlist watchlist);

    void deleteByIdAndUserId(UUID id, UUID userId);

    List<WatchlistItem> findItemsByWatchlistIds(Collection<UUID> watchlistIds);

    Optional<WatchlistItem> findItemByIdAndWatchlistId(UUID itemId, UUID watchlistId);

    boolean existsItemByWatchlistIdAndInstrumentId(UUID watchlistId, UUID instrumentId);

    WatchlistItem saveItem(WatchlistItem item);

    void deleteItemByIdAndWatchlistId(UUID itemId, UUID watchlistId);
}
