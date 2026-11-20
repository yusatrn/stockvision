package com.stockvision.scoring.api;

import com.stockvision.scoring.domain.RiskRewardMetrics;
import java.math.BigDecimal;

/**
 * Mathematical risk distance and reward-to-risk multiples relative to technical reference zones.
 */
public record RiskRewardMetricsResponse(
        BigDecimal riskDistancePercent,
        BigDecimal upsideToR1Percent,
        BigDecimal upsideToR2Percent,
        BigDecimal riskRewardToR1,
        BigDecimal riskRewardToR2) {

    public static RiskRewardMetricsResponse from(RiskRewardMetrics metrics) {
        if (metrics == null) {
            return null;
        }
        return new RiskRewardMetricsResponse(
                metrics.riskDistancePercent(),
                metrics.upsideToR1Percent(),
                metrics.upsideToR2Percent(),
                metrics.riskRewardToR1(),
                metrics.riskRewardToR2());
    }
}
