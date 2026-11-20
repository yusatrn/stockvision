package com.stockvision.scoring.domain;

import com.stockvision.marketdata.domain.BarInterval;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persisted technical scoring execution record.
 */
public record TechnicalScoreRun(
        UUID id,
        UUID analysisRunId,
        UUID instrumentId,
        String symbol,
        String exchangeCode,
        BarInterval interval,
        String provider,
        boolean adjusted,
        boolean simulated,
        String algorithmVersion,
        ScoreStatus status,
        boolean staleData,
        BigDecimal finalScore,
        ScoreRegime regime,
        TrendDirection trendDirection,
        BigDecimal latestClose,
        BigDecimal latestRsi,
        String weightsJson,
        String componentsJson,
        String explanationJson,
        Instant rangeFrom,
        Instant rangeTo,
        Instant latestBarTimestamp,
        Instant calculatedAt) {

    public TechnicalScoreRun {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(analysisRunId, "analysisRunId must not be null");
        Objects.requireNonNull(instrumentId, "instrumentId must not be null");
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(exchangeCode, "exchangeCode must not be null");
        Objects.requireNonNull(interval, "interval must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(algorithmVersion, "algorithmVersion must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(regime, "regime must not be null");
        Objects.requireNonNull(trendDirection, "trendDirection must not be null");
        Objects.requireNonNull(weightsJson, "weightsJson must not be null");
        Objects.requireNonNull(componentsJson, "componentsJson must not be null");
        Objects.requireNonNull(explanationJson, "explanationJson must not be null");
        Objects.requireNonNull(calculatedAt, "calculatedAt must not be null");
    }
}
