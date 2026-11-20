package com.stockvision.scoring.domain;

import com.stockvision.common.domain.FinancialMath;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configurable component weights summing strictly to {@code 100.00%}.
 */
public final class ScoreWeights {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    private final Map<ScoreComponentType, BigDecimal> weights;

    private ScoreWeights(Map<ScoreComponentType, BigDecimal> weights) {
        Map<ScoreComponentType, BigDecimal> ordered = new LinkedHashMap<>();
        for (ScoreComponentType type : ScoreComponentType.values()) {
            ordered.put(type, weights.get(type));
        }
        this.weights = Collections.unmodifiableMap(ordered);
    }

    public static ScoreWeights defaults() {
        Map<ScoreComponentType, BigDecimal> defaults = new EnumMap<>(ScoreComponentType.class);
        defaults.put(ScoreComponentType.TREND, new BigDecimal("20.00"));
        defaults.put(ScoreComponentType.RSI, new BigDecimal("15.00"));
        defaults.put(ScoreComponentType.MACD, new BigDecimal("15.00"));
        defaults.put(ScoreComponentType.VOLUME, new BigDecimal("15.00"));
        defaults.put(ScoreComponentType.BOLLINGER, new BigDecimal("10.00"));
        defaults.put(ScoreComponentType.MOMENTUM, new BigDecimal("10.00"));
        defaults.put(ScoreComponentType.SUPPORT_RESISTANCE, new BigDecimal("15.00"));
        return ofExact(defaults);
    }

    public static ScoreWeights ofExact(Map<ScoreComponentType, BigDecimal> rawWeights) {
        validateAllPresentNonNegative(rawWeights);
        Map<ScoreComponentType, BigDecimal> scaled = new EnumMap<>(ScoreComponentType.class);
        BigDecimal sum = BigDecimal.ZERO;
        for (ScoreComponentType type : ScoreComponentType.values()) {
            BigDecimal weight = FinancialMath.scaleMoney(rawWeights.get(type));
            scaled.put(type, weight);
            sum = sum.add(weight);
        }
        if (sum.compareTo(ONE_HUNDRED) != 0) {
            throw new IllegalArgumentException(
                    "Scoring weights must sum to exactly 100.00%, but summed to " + sum);
        }
        return new ScoreWeights(scaled);
    }

    public static ScoreWeights normalize(Map<ScoreComponentType, BigDecimal> rawWeights) {
        validateAllPresentNonNegative(rawWeights);
        BigDecimal rawTotal = BigDecimal.ZERO;
        ScoreComponentType largestType = ScoreComponentType.TREND;
        BigDecimal largestRaw = BigDecimal.valueOf(-1L);

        for (ScoreComponentType type : ScoreComponentType.values()) {
            BigDecimal value = rawWeights.get(type);
            rawTotal = rawTotal.add(value, FinancialMath.MATH_CONTEXT);
            if (value.compareTo(largestRaw) > 0) {
                largestRaw = value;
                largestType = type;
            }
        }

        if (rawTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Sum of raw weights must be strictly positive");
        }

        Map<ScoreComponentType, BigDecimal> normalized = new EnumMap<>(ScoreComponentType.class);
        BigDecimal normalizedSum = BigDecimal.ZERO;
        for (ScoreComponentType type : ScoreComponentType.values()) {
            BigDecimal scaled =
                    rawWeights
                            .get(type)
                            .multiply(ONE_HUNDRED, FinancialMath.MATH_CONTEXT)
                            .divide(rawTotal, FinancialMath.MONEY_SCALE, FinancialMath.ROUNDING_MODE);
            normalized.put(type, scaled);
            normalizedSum = normalizedSum.add(scaled);
        }

        BigDecimal residual = ONE_HUNDRED.subtract(normalizedSum);
        if (residual.compareTo(BigDecimal.ZERO) != 0) {
            normalized.put(largestType, normalized.get(largestType).add(residual));
        }
        return ofExact(normalized);
    }

    public BigDecimal weightOf(ScoreComponentType type) {
        return weights.get(Objects.requireNonNull(type, "type must not be null"));
    }

    public Map<ScoreComponentType, BigDecimal> asMap() {
        return weights;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ScoreWeights other)) {
            return false;
        }
        return weights.equals(other.weights);
    }

    @Override
    public int hashCode() {
        return Objects.hash(weights);
    }

    private static void validateAllPresentNonNegative(Map<ScoreComponentType, BigDecimal> map) {
        Objects.requireNonNull(map, "weights map must not be null");
        for (ScoreComponentType type : ScoreComponentType.values()) {
            BigDecimal value = map.get(type);
            if (value == null) {
                throw new IllegalArgumentException("Missing weight for component: " + type);
            }
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                        "Weight must not be negative for component: " + type);
            }
        }
    }
}
