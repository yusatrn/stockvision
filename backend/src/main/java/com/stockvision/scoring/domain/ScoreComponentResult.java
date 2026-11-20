package com.stockvision.scoring.domain;

import com.stockvision.technicalanalysis.domain.IndicatorStatus;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Individual component evaluation with normalized [-100, +100] contribution, weight, metrics, and
 * human-readable rationale.
 */
public record ScoreComponentResult(
        ScoreComponentType type,
        IndicatorStatus status,
        BigDecimal weightPercent,
        BigDecimal normalizedScore,
        BigDecimal weightedContribution,
        String explanation,
        Map<String, BigDecimal> metrics) {

    public ScoreComponentResult {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(weightPercent, "weightPercent must not be null");
        Objects.requireNonNull(explanation, "explanation must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
        metrics = Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
    }
}
