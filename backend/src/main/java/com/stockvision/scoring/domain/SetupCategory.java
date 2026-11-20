package com.stockvision.scoring.domain;

import java.util.Arrays;
import java.util.Locale;

/**
 * Descriptive rule-based technical posture category for candidate screening. Never represents a
 * profit guarantee or personalized investment recommendation.
 */
public enum SetupCategory {
    HIGH_MOMENTUM_BREAKOUT,
    PULLBACK_WATCH,
    OVEREXTENDED_RISK,
    CONSOLIDATION,
    WEAK_BEARISH,
    INSUFFICIENT_DATA;

    public static SetupCategory fromCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("category must not be blank");
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(value -> value.name().equals(normalized))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Unsupported setup category '"
                                                + rawCode
                                                + "'. Supported values: HIGH_MOMENTUM_BREAKOUT, PULLBACK_WATCH, "
                                                + "OVEREXTENDED_RISK, CONSOLIDATION, WEAK_BEARISH, INSUFFICIENT_DATA"));
    }
}
