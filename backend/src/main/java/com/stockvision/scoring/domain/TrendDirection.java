package com.stockvision.scoring.domain;

import java.util.Arrays;
import java.util.Locale;

/**
 * Descriptive moving-average trend classification for scoring and screener filtering.
 */
public enum TrendDirection {
    BULLISH,
    NEUTRAL,
    BEARISH,
    UNKNOWN;

    public static TrendDirection fromCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("trend must not be blank");
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(value -> value.name().equals(normalized))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Unsupported trend filter '"
                                                + rawCode
                                                + "'. Supported values: BULLISH, NEUTRAL, BEARISH, UNKNOWN"));
    }
}
