package com.stockvision.scoring.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stockvision.scoring.domain.ScoreComponentResult;
import com.stockvision.scoring.domain.ScoreComponentType;
import com.stockvision.technicalanalysis.domain.IndicatorStatus;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Individual technical score component contribution and rationale.
 */
public record ScoreComponentResponse(
        ScoreComponentType type,
        IndicatorStatus status,
        BigDecimal weightPercent,
        BigDecimal normalizedScore,
        BigDecimal weightedContribution,
        String explanation,
        @JsonInclude(JsonInclude.Include.ALWAYS) Map<String, BigDecimal> metrics) {

    public static ScoreComponentResponse from(ScoreComponentResult component) {
        return new ScoreComponentResponse(
                component.type(),
                component.status(),
                component.weightPercent(),
                component.normalizedScore(),
                component.weightedContribution(),
                component.explanation(),
                component.metrics());
    }
}
