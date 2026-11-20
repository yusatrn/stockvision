package com.stockvision.scoring.api;

import com.stockvision.scoring.domain.ScoreRegime;
import com.stockvision.scoring.domain.ScoreStatus;
import com.stockvision.scoring.domain.TrendDirection;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Screener summary row representing an instrument's latest technical score and trend posture.
 */
public record ScreenerItemResponse(
        UUID scoreRunId,
        String symbol,
        String name,
        String exchange,
        String sector,
        String currency,
        ScoreStatus status,
        BigDecimal finalScore,
        ScoreRegime regime,
        TrendDirection trendDirection,
        BigDecimal latestClose,
        BigDecimal latestRsi,
        boolean simulated,
        boolean staleData,
        String headline,
        Instant calculatedAt) {
}
