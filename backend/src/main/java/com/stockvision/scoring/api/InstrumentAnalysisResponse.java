package com.stockvision.scoring.api;

import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import com.stockvision.marketdata.api.FreshnessMetadataResponse;
import com.stockvision.scoring.domain.ScoreRegime;
import com.stockvision.scoring.domain.ScoreStatus;
import com.stockvision.scoring.domain.TrendDirection;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Full explainable technical analysis and 0–100 scoring response for a single instrument.
 */
public record InstrumentAnalysisResponse(
        UUID scoreRunId,
        UUID analysisRunId,
        String symbol,
        String exchange,
        String interval,
        boolean adjusted,
        Instant from,
        Instant to,
        int barCount,
        String algorithmVersion,
        String indicatorEngineVersion,
        ScoreStatus status,
        boolean staleData,
        BigDecimal finalScore,
        ScoreRegime regime,
        TrendDirection trendDirection,
        BigDecimal latestClose,
        BigDecimal latestRsi,
        List<String> insufficientDataReasons,
        Map<String, BigDecimal> weights,
        List<ScoreComponentResponse> components,
        ScoreExplanationResponse explanation,
        Instant calculatedAt,
        DataSourceMetadataResponse dataSource,
        FreshnessMetadataResponse freshness) {
}
