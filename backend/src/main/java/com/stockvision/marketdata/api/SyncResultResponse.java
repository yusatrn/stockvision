package com.stockvision.marketdata.api;

import com.stockvision.marketdata.domain.ProviderSyncRun;
import com.stockvision.marketdata.domain.SyncRunStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Result of an idempotent provider synchronization run.
 */
public record SyncResultResponse(
        UUID syncRunId,
        String symbol,
        String interval,
        boolean adjusted,
        boolean simulated,
        String provider,
        SyncRunStatus status,
        int barsFetched,
        int barsInserted,
        int barsSkipped,
        Instant rangeFrom,
        Instant rangeTo,
        Instant completedAt) {

    public static SyncResultResponse from(ProviderSyncRun run) {
        return new SyncResultResponse(
                run.id(),
                run.symbol(),
                run.interval() != null ? run.interval().code() : null,
                run.adjusted(),
                run.simulated(),
                run.provider(),
                run.status(),
                run.barsFetched(),
                run.barsInserted(),
                run.barsSkipped(),
                run.rangeFrom(),
                run.rangeTo(),
                run.completedAt());
    }
}
