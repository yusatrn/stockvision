package com.stockvision.scoring.api;

import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import com.stockvision.marketdata.api.FreshnessMetadataResponse;
import com.stockvision.scoring.domain.ScoreRegime;
import com.stockvision.scoring.domain.ScoreStatus;
import com.stockvision.scoring.domain.SetupCategory;
import com.stockvision.scoring.domain.TechnicalSetupResult;
import com.stockvision.scoring.domain.TrendDirection;
import com.stockvision.stock.domain.Instrument;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Single-instrument rule-based technical levels, BIST margin reference, and risk/reward analysis.
 */
public record InstrumentSetupResponse(
        String symbol,
        String name,
        String exchange,
        String sector,
        String currency,
        String interval,
        boolean adjusted,
        Instant from,
        Instant to,
        int barCount,
        String setupEngineVersion,
        String scoringAlgorithmVersion,
        String indicatorEngineVersion,
        ScoreStatus status,
        SetupCategory category,
        boolean staleData,
        boolean simulated,
        BigDecimal technicalScore,
        BigDecimal momentumBreakoutScore,
        ScoreRegime regime,
        TrendDirection trendDirection,
        BigDecimal technicalReferencePrice,
        BigDecimal latestRsi,
        BigDecimal volumeRatio,
        BigDecimal atr14,
        BigDecimal atrPercent,
        BigDecimal breakoutTriggerLevel,
        BigDecimal distanceToBreakoutPercent,
        boolean breakoutConfirmed,
        PriceZoneResponse supportWatchZone,
        PriceZoneResponse resistanceExitZone,
        BigDecimal invalidationRiskLevel,
        String invalidationRationale,
        RiskRewardMetricsResponse riskReward,
        BistMarginReferenceResponse bistMargin,
        List<String> usedIndicators,
        List<String> rationaleItems,
        List<String> riskWarnings,
        List<String> insufficientDataReasons,
        String headline,
        Instant calculatedAt,
        String disclaimer,
        DataSourceMetadataResponse dataSource,
        FreshnessMetadataResponse freshness) {

    public static InstrumentSetupResponse from(
            Instrument instrument,
            String interval,
            boolean adjusted,
            Instant from,
            Instant to,
            int barCount,
            String scoringAlgorithmVersion,
            String indicatorEngineVersion,
            TechnicalSetupResult setup,
            boolean simulated,
            Instant calculatedAt,
            DataSourceMetadataResponse dataSource,
            FreshnessMetadataResponse freshness) {
        return new InstrumentSetupResponse(
                instrument.symbol(),
                instrument.name(),
                instrument.exchangeCode(),
                instrument.sector(),
                instrument.currency(),
                interval,
                adjusted,
                from,
                to,
                barCount,
                setup.engineVersion(),
                scoringAlgorithmVersion,
                indicatorEngineVersion,
                setup.status(),
                setup.category(),
                setup.staleData(),
                simulated,
                setup.technicalScore(),
                setup.momentumBreakoutScore(),
                setup.regime(),
                setup.trendDirection(),
                setup.technicalReferencePrice(),
                setup.latestRsi(),
                setup.volumeRatio(),
                setup.atr14(),
                setup.atrPercent(),
                setup.breakoutTriggerLevel(),
                setup.distanceToBreakoutPercent(),
                setup.breakoutConfirmed(),
                PriceZoneResponse.from(setup.supportWatchZone()),
                PriceZoneResponse.from(setup.resistanceExitZone()),
                setup.invalidationRiskLevel(),
                setup.invalidationRationale(),
                RiskRewardMetricsResponse.from(setup.riskReward()),
                BistMarginReferenceResponse.from(setup.bistMargin()),
                setup.usedIndicators(),
                setup.rationaleItems(),
                setup.riskWarnings(),
                setup.insufficientDataReasons(),
                setup.headline(),
                calculatedAt,
                setup.disclaimer(),
                dataSource,
                freshness);
    }
}
