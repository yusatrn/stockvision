package com.stockvision.watchlist.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Single instrument membership within a user's {@link Watchlist}.
 */
public record WatchlistItem(
        UUID id,
        UUID watchlistId,
        UUID instrumentId,
        String notes,
        Instant createdAt) {

    public WatchlistItem {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(watchlistId, "watchlistId must not be null");
        Objects.requireNonNull(instrumentId, "instrumentId must not be null");
    }
}
