package com.stockvision.scoring.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScoreWeightsTest {

    @Test
    void defaultWeightsSumToOneHundred() {
        ScoreWeights defaults = ScoreWeights.defaults();
        BigDecimal sum =
                defaults.asMap().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(sum).isEqualByComparingTo("100.00");
        assertThat(defaults.weightOf(ScoreComponentType.TREND)).isEqualByComparingTo("20.00");
        assertThat(defaults.weightOf(ScoreComponentType.SUPPORT_RESISTANCE))
                .isEqualByComparingTo("15.00");
    }

    @Test
    void normalizesArbitraryPositiveWeightsToExactOneHundred() {
        Map<ScoreComponentType, BigDecimal> equalWeights = new EnumMap<>(ScoreComponentType.class);
        for (ScoreComponentType type : ScoreComponentType.values()) {
            equalWeights.put(type, BigDecimal.ONE);
        }

        ScoreWeights normalized = ScoreWeights.normalize(equalWeights);
        BigDecimal sum =
                normalized.asMap().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(sum).isEqualByComparingTo("100.00");
        assertThat(normalized.asMap().values())
                .allMatch(w -> w.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void rejectsInvalidExactSumMissingComponentOrNegativeWeight() {
        Map<ScoreComponentType, BigDecimal> invalidSum = new EnumMap<>(ScoreComponentType.class);
        for (ScoreComponentType type : ScoreComponentType.values()) {
            invalidSum.put(type, new BigDecimal("10"));
        }

        assertThatThrownBy(() -> ScoreWeights.ofExact(invalidSum))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100.00");

        invalidSum.put(ScoreComponentType.TREND, new BigDecimal("-1"));
        assertThatThrownBy(() -> ScoreWeights.normalize(invalidSum))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");

        invalidSum.put(ScoreComponentType.TREND, new BigDecimal("20"));
        invalidSum.remove(ScoreComponentType.RSI);
        assertThatThrownBy(() -> ScoreWeights.ofExact(invalidSum))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing");
    }
}
