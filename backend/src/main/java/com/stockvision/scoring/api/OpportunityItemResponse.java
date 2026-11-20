package com.stockvision.scoring.api;

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
 * Rule-based momentum setup and technical reference level item for an active exchange instrument.
 */
public record OpportunityItemResponse(
        String symbol,
        String name,
        String exchange,
        String sector,
        String currency,
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
        Instant calculatedAt) {

    public static OpportunityItemResponse from(
            Instrument instrument,
            TechnicalSetupResult setup,
            boolean simulated,
            Instant calculatedAt) {
        return new OpportunityItemResponse(
                instrument.symbol(),
                instrument.name(),
                instrument.exchangeCode(),
                instrument.sector(),
                instrument.currency(),
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
                calculatedAt);
    }
}
