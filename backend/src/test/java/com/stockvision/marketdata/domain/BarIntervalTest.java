package com.stockvision.marketdata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BarIntervalTest {

    @Test
    void parsesSupportedIntervals() {
        assertThat(BarInterval.fromCode("1d")).isEqualTo(BarInterval.ONE_DAY);
        assertThat(BarInterval.fromCode(" 1H ")).isEqualTo(BarInterval.ONE_HOUR);
        assertThat(BarInterval.fromCode("15m")).isEqualTo(BarInterval.FIFTEEN_MINUTES);
    }

    @Test
    void rejectsUnsupportedIntervals() {
        assertThatThrownBy(() -> BarInterval.fromCode("5y"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported interval");
        assertThatThrownBy(() -> BarInterval.fromCode(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
