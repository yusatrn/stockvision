package com.stockvision.watchlist.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * User-owned named watchlist domain model.
 */
public record Watchlist(
        UUID id,
        UUID userId,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt) {

    public Watchlist {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
