package com.stockvision.watchlist.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Named watchlist representation with its ordered items.
 */
public record WatchlistResponse(
        UUID id,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt,
        List<WatchlistItemResponse> items) {
}
