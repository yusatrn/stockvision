package com.stockvision.technicalanalysis.domain;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Computed indicator series along with warm-up metadata and sufficiency status.
 */
public record IndicatorSeries(
        IndicatorType type,
        IndicatorStatus status,
        int warmUpBars,
        int requiredBars,
        int availableBars,
        Map<String, Object> parameters,
        Map<String, BigDecimal> latestValues,
        List<IndicatorPoint> points) {

    public IndicatorSeries {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(parameters, "parameters must not be null");
        Objects.requireNonNull(latestValues, "latestValues must not be null");
        Objects.requireNonNull(points, "points must not be null");
        parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        latestValues = Collections.unmodifiableMap(new LinkedHashMap<>(latestValues));
        points = List.copyOf(points);
    }
}
