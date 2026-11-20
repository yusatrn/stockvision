package com.stockvision.marketdata.domain;

import java.util.Arrays;
import java.util.Locale;

/**
 * Supported historical OHLCV bar intervals and maximum permitted query span per request.
 */
public enum BarInterval {
    ONE_DAY("1d", 3650),
    ONE_HOUR("1h", 180),
    FIFTEEN_MINUTES("15m", 30);

    private final String code;
    private final int maxRangeDays;

    BarInterval(String code, int maxRangeDays) {
        this.code = code;
        this.maxRangeDays = maxRangeDays;
    }

    public String code() {
        return code;
    }

    public int maxRangeDays() {
        return maxRangeDays;
    }

    public static BarInterval fromCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("interval must not be blank");
        }
        String normalized = rawCode.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(interval -> interval.code.equals(normalized))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Unsupported interval '"
                                                + rawCode
                                                + "'. Supported values: 1d, 1h, 15m"));
    }
}
