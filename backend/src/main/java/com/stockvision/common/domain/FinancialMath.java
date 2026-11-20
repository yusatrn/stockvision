package com.stockvision.common.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Shared {@link BigDecimal} precision and rounding policy for prices, monetary amounts, quantities,
 * percentages, and technical indicators.
 *
 * <p>All calculations use {@link RoundingMode#HALF_UP} and prohibit floating-point types.
 */
public final class FinancialMath {

    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    public static final int PRICE_SCALE = 4;
    public static final int MONEY_SCALE = 2;
    public static final int QUANTITY_SCALE = 6;
    public static final int INDICATOR_SCALE = 6;
    public static final int PERCENTAGE_SCALE = 4;
    public static final MathContext MATH_CONTEXT = new MathContext(16, ROUNDING_MODE);

    private FinancialMath() {
    }

    public static BigDecimal scalePrice(BigDecimal value) {
        return scale(value, PRICE_SCALE);
    }

    public static BigDecimal scaleMoney(BigDecimal value) {
        return scale(value, MONEY_SCALE);
    }

    public static BigDecimal scaleQuantity(BigDecimal value) {
        return scale(value, QUANTITY_SCALE);
    }

    public static BigDecimal scaleIndicator(BigDecimal value) {
        return scale(value, INDICATOR_SCALE);
    }

    public static BigDecimal scalePercentage(BigDecimal value) {
        return scale(value, PERCENTAGE_SCALE);
    }

    public static BigDecimal scale(BigDecimal value, int targetScale) {
        Objects.requireNonNull(value, "value must not be null");
        if (targetScale < 0) {
            throw new IllegalArgumentException("targetScale must not be negative");
        }
        return value.setScale(targetScale, ROUNDING_MODE);
    }

    public static BigDecimal divide(BigDecimal numerator, BigDecimal denominator, int targetScale) {
        Objects.requireNonNull(numerator, "numerator must not be null");
        Objects.requireNonNull(denominator, "denominator must not be null");
        if (targetScale < 0) {
            throw new IllegalArgumentException("targetScale must not be negative");
        }
        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("denominator must not be zero");
        }
        return numerator.divide(denominator, targetScale, ROUNDING_MODE);
    }
}
