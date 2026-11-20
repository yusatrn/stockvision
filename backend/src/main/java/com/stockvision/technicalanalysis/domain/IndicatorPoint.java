package com.stockvision.technicalanalysis.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Single timestamped indicator sample. Warm-up or undefined sub-values are represented explicitly
 * as {@code null} map entries rather than fabricated defaults.
 */
public record IndicatorPoint(
        Instant timestamp,
        boolean warmUp,
        Map<String, BigDecimal> values) {

    public IndicatorPoint {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(values, "values must not be null");
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
