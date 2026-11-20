package com.stockvision.portfolio.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * User-owned virtual portfolio definition. Totals and holdings are never persisted redundantly.
 */
public record Portfolio(
        UUID id,
        UUID userId,
        String name,
        String description,
        String baseCurrency,
        FeeTaxPolicy feeTaxPolicy,
        Instant createdAt,
        Instant updatedAt) {

    public Portfolio {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(baseCurrency, "baseCurrency must not be null");
        Objects.requireNonNull(feeTaxPolicy, "feeTaxPolicy must not be null");
    }
}
