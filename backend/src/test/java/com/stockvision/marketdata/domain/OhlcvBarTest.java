package com.stockvision.marketdata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OhlcvBarTest {

    private static final Instant TS = Instant.parse("2026-11-18T15:00:00Z");

    @Test
    void scalesPricesWithHalfUpAndAcceptsValidInvariants() {
        OhlcvBar bar =
                new OhlcvBar(
                        TS,
                        new BigDecimal("100.12345"),
                        new BigDecimal("105.0000"),
                        new BigDecimal("99.5000"),
                        new BigDecimal("102.34565"),
                        0L,
                        "TRY",
                        BarInterval.ONE_DAY,
                        "MOCK_SIMULATED",
                        false,
                        true,
                        "Europe/Istanbul",
                        TS);

        assertThat(bar.open()).isEqualByComparingTo("100.1235");
        assertThat(bar.close()).isEqualByComparingTo("102.3457");
        assertThat(bar.volume()).isZero();
    }

    @Test
    void rejectsOhlcvInvariantViolations() {
        assertThatThrownBy(
                        () ->
                                new OhlcvBar(
                                        TS,
                                        new BigDecimal("100"),
                                        new BigDecimal("99"),
                                        new BigDecimal("95"),
                                        new BigDecimal("98"),
                                        100L,
                                        "TRY",
                                        BarInterval.ONE_DAY,
                                        "MOCK_SIMULATED",
                                        false,
                                        true,
                                        "Europe/Istanbul",
                                        TS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("high");

        assertThatThrownBy(
                        () ->
                                new OhlcvBar(
                                        TS,
                                        new BigDecimal("100"),
                                        new BigDecimal("105"),
                                        new BigDecimal("101"),
                                        new BigDecimal("103"),
                                        100L,
                                        "TRY",
                                        BarInterval.ONE_DAY,
                                        "MOCK_SIMULATED",
                                        false,
                                        true,
                                        "Europe/Istanbul",
                                        TS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("low");

        assertThatThrownBy(
                        () ->
                                new OhlcvBar(
                                        TS,
                                        BigDecimal.ZERO,
                                        new BigDecimal("105"),
                                        BigDecimal.ZERO,
                                        new BigDecimal("103"),
                                        100L,
                                        "TRY",
                                        BarInterval.ONE_DAY,
                                        "MOCK_SIMULATED",
                                        false,
                                        true,
                                        "Europe/Istanbul",
                                        TS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");

        assertThatThrownBy(
                        () ->
                                new OhlcvBar(
                                        TS,
                                        new BigDecimal("100"),
                                        new BigDecimal("105"),
                                        new BigDecimal("95"),
                                        new BigDecimal("102"),
                                        -1L,
                                        "TRY",
                                        BarInterval.ONE_DAY,
                                        "MOCK_SIMULATED",
                                        false,
                                        true,
                                        "Europe/Istanbul",
                                        TS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("volume");
    }
}
