package com.stockvision.technicalanalysis.domain;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Parameter configuration for deterministic technical indicator calculations.
 */
public record IndicatorConfiguration(
        int smaPeriod,
        int emaPeriod,
        int rsiPeriod,
        int macdFastPeriod,
        int macdSlowPeriod,
        int macdSignalPeriod,
        int bollingerPeriod,
        BigDecimal bollingerStdDevMultiplier,
        int stochasticKPeriod,
        int stochasticDPeriod,
        int atrPeriod,
        int volumeAvgPeriod) {

    private static final BigDecimal MIN_STD_DEV = new BigDecimal("0.5");
    private static final BigDecimal MAX_STD_DEV = new BigDecimal("5.0");

    public IndicatorConfiguration {
        validateRange("smaPeriod", smaPeriod, 2, 200);
        validateRange("emaPeriod", emaPeriod, 2, 200);
        validateRange("rsiPeriod", rsiPeriod, 2, 100);
        validateRange("macdFastPeriod", macdFastPeriod, 2, 100);
        validateRange("macdSlowPeriod", macdSlowPeriod, 3, 200);
        validateRange("macdSignalPeriod", macdSignalPeriod, 2, 100);
        if (macdFastPeriod >= macdSlowPeriod) {
            throw new IllegalArgumentException(
                    "macdFastPeriod must be strictly less than macdSlowPeriod");
        }
        validateRange("bollingerPeriod", bollingerPeriod, 2, 200);
        Objects.requireNonNull(
                bollingerStdDevMultiplier, "bollingerStdDevMultiplier must not be null");
        if (bollingerStdDevMultiplier.compareTo(MIN_STD_DEV) < 0
                || bollingerStdDevMultiplier.compareTo(MAX_STD_DEV) > 0) {
            throw new IllegalArgumentException(
                    "bollingerStdDevMultiplier must be between 0.5 and 5.0");
        }
        validateRange("stochasticKPeriod", stochasticKPeriod, 2, 100);
        validateRange("stochasticDPeriod", stochasticDPeriod, 1, 50);
        validateRange("atrPeriod", atrPeriod, 2, 100);
        validateRange("volumeAvgPeriod", volumeAvgPeriod, 2, 200);
    }

    public static IndicatorConfiguration defaults() {
        return new IndicatorConfiguration(
                20,
                20,
                14,
                12,
                26,
                9,
                20,
                new BigDecimal("2.0"),
                14,
                3,
                14,
                20);
    }

    public Map<String, Object> forIndicator(IndicatorType type) {
        Map<String, Object> params = new LinkedHashMap<>();
        switch (type) {
            case SMA -> params.put("period", smaPeriod);
            case EMA -> params.put("period", emaPeriod);
            case RSI -> {
                params.put("period", rsiPeriod);
                params.put("smoothing", "WILDER_RMA");
            }
            case MACD -> {
                params.put("fastPeriod", macdFastPeriod);
                params.put("slowPeriod", macdSlowPeriod);
                params.put("signalPeriod", macdSignalPeriod);
            }
            case BOLLINGER -> {
                params.put("period", bollingerPeriod);
                params.put("stdDevMultiplier", bollingerStdDevMultiplier);
                params.put("stdDevPopulation", true);
            }
            case STOCHASTIC -> {
                params.put("kPeriod", stochasticKPeriod);
                params.put("dPeriod", stochasticDPeriod);
            }
            case ATR -> {
                params.put("period", atrPeriod);
                params.put("smoothing", "WILDER_RMA");
            }
            case VOLUME_AVG -> params.put("period", volumeAvgPeriod);
        }
        return Collections.unmodifiableMap(params);
    }

    public Map<String, Object> toParameterMap(Set<IndicatorType> requestedIndicators) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (IndicatorType type : requestedIndicators) {
            map.put(type.name(), forIndicator(type));
        }
        return Collections.unmodifiableMap(map);
    }

    private static void validateRange(String field, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    field + " must be between " + min + " and " + max);
        }
    }
}
