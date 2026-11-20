package com.stockvision.scoring.domain;

import java.util.Arrays;
import java.util.Locale;

/**
 * Defines which slice of an exchange's active instrument catalog is evaluated by screener and
 * opportunity endpoints.
 */
public enum UniverseScope {
    BIST30,
    BIST100,
    SYNCED,
    ALL;

    public static UniverseScope fromCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return BIST30;
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(scope -> scope.name().equals(normalized))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Unsupported universe scope '"
                                                + rawCode
                                                + "'. Supported values: BIST30, BIST100, SYNCED, ALL"));
    }
}
