package com.stockvision.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FinancialMathTest {

    @Test
    void scalesValuesUsingHalfUpRoundingPolicy() {
        assertThat(FinancialMath.scalePrice(new BigDecimal("123.45675")))
                .isEqualByComparingTo("123.4568");
        assertThat(FinancialMath.scaleMoney(new BigDecimal("10.125")))
                .isEqualByComparingTo("10.13");
        assertThat(FinancialMath.scaleMoney(new BigDecimal("-10.125")))
                .isEqualByComparingTo("-10.13");
        assertThat(FinancialMath.scaleQuantity(new BigDecimal("5.1234565")))
                .isEqualByComparingTo("5.123457");
        assertThat(FinancialMath.scaleIndicator(new BigDecimal("55.1234564")))
                .isEqualByComparingTo("55.123456");
        assertThat(FinancialMath.scalePercentage(new BigDecimal("0.12345")))
                .isEqualByComparingTo("0.1235");
    }

    @Test
    void dividesUsingExplicitScaleAndRoundingMode() {
        BigDecimal result =
                FinancialMath.divide(
                        new BigDecimal("10"),
                        new BigDecimal("3"),
                        FinancialMath.INDICATOR_SCALE);

        assertThat(result).isEqualByComparingTo("3.333333");
    }

    @Test
    void rejectsNullsZeroDivisorAndNegativeScale() {
        assertThatThrownBy(() -> FinancialMath.scalePrice(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> FinancialMath.scale(BigDecimal.ONE, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> FinancialMath.divide(BigDecimal.TEN, BigDecimal.ZERO, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zero");
        assertThatThrownBy(
                        () -> FinancialMath.divide(null, BigDecimal.ONE, 2))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () -> FinancialMath.divide(BigDecimal.ONE, null, 2))
                .isInstanceOf(NullPointerException.class);
    }
}
