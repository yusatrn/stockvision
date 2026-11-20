package com.stockvision.marketdata.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Audit record describing a market data provider sync execution and its outcome.
 */
public record ProviderSyncRun(
        UUID id,
        String provider,
        UUID instrumentId,
        String symbol,
        BarInterval interval,
        boolean adjusted,
        boolean simulated,
        SyncRunStatus status,
        int barsFetched,
        int barsInserted,
        int barsSkipped,
        Instant rangeFrom,
        Instant rangeTo,
        Instant startedAt,
        Instant completedAt,
        String errorMessage) {

    public ProviderSyncRun {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
    }
}
