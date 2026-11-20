package com.stockvision.technicalanalysis.domain;

import com.stockvision.marketdata.domain.BarInterval;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model representing a persisted technical analysis or scoring execution record.
 */
public record AnalysisRun(
        UUID id,
        UUID instrumentId,
        String symbol,
        BarInterval interval,
        String provider,
        boolean adjusted,
        boolean simulated,
        AnalysisRunType runType,
        String engineVersion,
        AnalysisRunStatus status,
        String parametersJson,
        int barCount,
        Instant rangeFrom,
        Instant rangeTo,
        Instant latestBarTimestamp,
        Instant calculatedAt) {

    public AnalysisRun {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(instrumentId, "instrumentId must not be null");
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(interval, "interval must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(runType, "runType must not be null");
        Objects.requireNonNull(engineVersion, "engineVersion must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(parametersJson, "parametersJson must not be null");
        Objects.requireNonNull(calculatedAt, "calculatedAt must not be null");
        if (barCount < 0) {
            throw new IllegalArgumentException("barCount must not be negative");
        }
    }
}
