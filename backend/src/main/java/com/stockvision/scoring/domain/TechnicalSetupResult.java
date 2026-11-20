package com.stockvision.scoring.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Complete rule-based technical setup, reference zone, invalidation boundary, and BIST margin
 * evaluation result with explicit {@code INSUFFICIENT_DATA} handling.
 */
public record TechnicalSetupResult(
        String engineVersion,
        ScoreStatus status,
        SetupCategory category,
        boolean staleData,
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
        PriceZone supportWatchZone,
        PriceZone resistanceExitZone,
        BigDecimal invalidationRiskLevel,
        String invalidationRationale,
        RiskRewardMetrics riskReward,
        BistMarginReference bistMargin,
        List<String> usedIndicators,
        List<String> rationaleItems,
        List<String> riskWarnings,
        List<String> insufficientDataReasons,
        String headline,
        String disclaimer) {

    public TechnicalSetupResult {
        Objects.requireNonNull(engineVersion, "engineVersion must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(regime, "regime must not be null");
        Objects.requireNonNull(trendDirection, "trendDirection must not be null");
        Objects.requireNonNull(usedIndicators, "usedIndicators must not be null");
        Objects.requireNonNull(rationaleItems, "rationaleItems must not be null");
        Objects.requireNonNull(riskWarnings, "riskWarnings must not be null");
        Objects.requireNonNull(
                insufficientDataReasons, "insufficientDataReasons must not be null");
        Objects.requireNonNull(headline, "headline must not be null");
        Objects.requireNonNull(disclaimer, "disclaimer must not be null");
        usedIndicators = List.copyOf(usedIndicators);
        rationaleItems = List.copyOf(rationaleItems);
        riskWarnings = List.copyOf(riskWarnings);
        insufficientDataReasons = List.copyOf(insufficientDataReasons);
    }
}
