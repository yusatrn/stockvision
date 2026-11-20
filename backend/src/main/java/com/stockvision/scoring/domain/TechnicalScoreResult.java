package com.stockvision.scoring.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Complete explainable technical scoring output with version, weights, components, and
 * {@code INSUFFICIENT_DATA} reasons when prerequisites are unmet.
 */
public record TechnicalScoreResult(
        String algorithmVersion,
        String indicatorEngineVersion,
        ScoreStatus status,
        BigDecimal finalScore,
        ScoreRegime regime,
        TrendDirection trendDirection,
        BigDecimal latestClose,
        BigDecimal latestRsi,
        boolean staleData,
        List<String> insufficientDataReasons,
        ScoreWeights weights,
        List<ScoreComponentResult> components,
        ScoreExplanation explanation) {

    public TechnicalScoreResult {
        Objects.requireNonNull(algorithmVersion, "algorithmVersion must not be null");
        Objects.requireNonNull(indicatorEngineVersion, "indicatorEngineVersion must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(regime, "regime must not be null");
        Objects.requireNonNull(trendDirection, "trendDirection must not be null");
        Objects.requireNonNull(insufficientDataReasons, "insufficientDataReasons must not be null");
        Objects.requireNonNull(weights, "weights must not be null");
        Objects.requireNonNull(components, "components must not be null");
        Objects.requireNonNull(explanation, "explanation must not be null");
        insufficientDataReasons = List.copyOf(insufficientDataReasons);
        components = List.copyOf(components);
    }
}
