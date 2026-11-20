package com.stockvision.scoring.domain;

import com.stockvision.common.domain.FinancialMath;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Mathematical distance and risk-to-reward ratios relative to rule-based technical levels.
 */
public record RiskRewardMetrics(
        BigDecimal riskDistancePercent,
        BigDecimal upsideToR1Percent,
        BigDecimal upsideToR2Percent,
        BigDecimal riskRewardToR1,
        BigDecimal riskRewardToR2) {

    public RiskRewardMetrics {
        riskDistancePercent =
                FinancialMath.scalePercentage(
                        Objects.requireNonNull(
                                riskDistancePercent, "riskDistancePercent must not be null"));
        upsideToR1Percent =
                FinancialMath.scalePercentage(
                        Objects.requireNonNull(
                                upsideToR1Percent, "upsideToR1Percent must not be null"));
        upsideToR2Percent =
                FinancialMath.scalePercentage(
                        Objects.requireNonNull(
                                upsideToR2Percent, "upsideToR2Percent must not be null"));
        riskRewardToR1 =
                FinancialMath.scaleMoney(
                        Objects.requireNonNull(riskRewardToR1, "riskRewardToR1 must not be null"));
        riskRewardToR2 =
                FinancialMath.scaleMoney(
                        Objects.requireNonNull(riskRewardToR2, "riskRewardToR2 must not be null"));
    }
}
