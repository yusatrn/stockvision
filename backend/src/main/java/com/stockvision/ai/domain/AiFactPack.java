package com.stockvision.ai.domain;

import com.stockvision.marketdata.domain.DataProviderMode;
import com.stockvision.marketdata.domain.FreshnessStatus;
import com.stockvision.scoring.domain.ScoreRegime;
import com.stockvision.scoring.domain.ScoreStatus;
import com.stockvision.scoring.domain.TrendDirection;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Grounded, sanitized input fact pack constructed solely from persisted analysis & score runs
 * and public market-data metadata. Contains zero user PII, watchlists, or portfolio holdings.
 */
public record AiFactPack(
        UUID analysisRunId,
        UUID scoreRunId,
        String symbol,
        String instrumentName,
        String exchangeCode,
        String sector,
        String currency,
        String interval,
        boolean adjusted,
        String algorithmVersion,
        ScoreStatus status,
        boolean staleData,
        BigDecimal finalScore,
        ScoreRegime regime,
        TrendDirection trendDirection,
        BigDecimal latestClose,
        BigDecimal latestRsi,
        Instant latestBarTimestamp,
        Instant calculatedAt,
        String providerDisplayName,
        DataProviderMode providerMode,
        boolean simulated,
        int delayMinutes,
        FreshnessStatus freshnessStatus,
        String freshnessNote,
        List<FactComponent> components,
        String headline,
        List<String> keyDrivers,
        List<String> riskAndDataNotes) {

    public record FactComponent(
            String type,
            String status,
            BigDecimal weightPercent,
            BigDecimal normalizedScore,
            BigDecimal weightedContribution,
            String explanation,
            Map<String, BigDecimal> metrics) {
    }
}
