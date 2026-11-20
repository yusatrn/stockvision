package com.stockvision.technicalanalysis.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stockvision.technicalanalysis.domain.IndicatorPoint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Timestamped indicator sample emitted in API responses, preserving explicit nulls for warm-up or
 * undefined sub-metrics.
 */
public record IndicatorPointResponse(
        Instant timestamp,
        boolean warmUp,
        @JsonInclude(JsonInclude.Include.ALWAYS) Map<String, BigDecimal> values) {

    public static IndicatorPointResponse from(IndicatorPoint point) {
        return new IndicatorPointResponse(point.timestamp(), point.warmUp(), point.values());
    }
}
