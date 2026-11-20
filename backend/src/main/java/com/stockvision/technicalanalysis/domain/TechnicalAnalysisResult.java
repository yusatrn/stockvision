package com.stockvision.technicalanalysis.domain;

import java.util.List;
import java.util.Objects;

/**
 * Aggregated output from a deterministic technical indicator calculation pass.
 */
public record TechnicalAnalysisResult(
        String engineVersion,
        AnalysisRunStatus status,
        int barCount,
        List<IndicatorSeries> series) {

    public TechnicalAnalysisResult {
        Objects.requireNonNull(engineVersion, "engineVersion must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(series, "series must not be null");
        series = List.copyOf(series);
    }
}
