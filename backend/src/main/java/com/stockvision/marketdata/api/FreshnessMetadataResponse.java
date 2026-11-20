package com.stockvision.marketdata.api;

import com.stockvision.marketdata.domain.FreshnessStatus;
import com.stockvision.marketdata.domain.SyncRunStatus;
import java.time.Instant;

/**
 * Data freshness and synchronization metadata surfaced alongside market data responses.
 */
public record FreshnessMetadataResponse(
        FreshnessStatus status,
        boolean stale,
        boolean simulated,
        Instant latestDataTimestamp,
        Instant lastSyncedAt,
        SyncRunStatus lastSyncStatus,
        Long ageMinutes,
        String note) {
}
