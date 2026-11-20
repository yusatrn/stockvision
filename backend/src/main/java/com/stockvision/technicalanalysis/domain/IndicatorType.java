package com.stockvision.technicalanalysis.domain;

import java.util.Arrays;
import java.util.Locale;

/**
 * Supported deterministic technical indicators.
 */
public enum IndicatorType {
    SMA,
    EMA,
    RSI,
    MACD,
    BOLLINGER,
    STOCHASTIC,
    ATR,
    VOLUME_AVG;

    public static IndicatorType fromCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("indicator type must not be blank");
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.name().equals(normalized))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Unsupported indicator '"
                                                + rawCode
                                                + "'. Supported values: SMA, EMA, RSI, MACD, BOLLINGER, STOCHASTIC, ATR, VOLUME_AVG"));
    }
}
