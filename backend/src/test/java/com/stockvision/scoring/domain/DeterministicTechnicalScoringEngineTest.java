package com.stockvision.scoring.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.marketdata.domain.OhlcvBar;
import com.stockvision.technicalanalysis.domain.DeterministicTechnicalIndicatorEngine;
import com.stockvision.technicalanalysis.domain.IndicatorConfiguration;
import com.stockvision.technicalanalysis.domain.IndicatorStatus;
import com.stockvision.technicalanalysis.domain.IndicatorType;
import com.stockvision.technicalanalysis.domain.TechnicalAnalysisResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicTechnicalScoringEngineTest {

    private static final Instant T0 = Instant.parse("2026-10-01T15:00:00Z");

    private final DeterministicTechnicalIndicatorEngine indicatorEngine =
            new DeterministicTechnicalIndicatorEngine();
    private final DeterministicTechnicalScoringEngine scoringEngine =
            new DeterministicTechnicalScoringEngine();

    @Test
    void scoresBoundaryRegimesDeterministicallyWithoutGuaranteeLanguage() {
        List<OhlcvBar> uptrendBars = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            BigDecimal close = BigDecimal.valueOf(100L + i * 2L);
            uptrendBars.add(
                    bar(
                            i,
                            close.subtract(BigDecimal.ONE),
                            close.add(BigDecimal.ONE),
                            close.subtract(BigDecimal.TWO),
                            close,
                            1_000_000L + (i * 25_000L)));
        }

        TechnicalAnalysisResult upIndicators =
                indicatorEngine.calculate(
                        uptrendBars,
                        List.of(IndicatorType.values()),
                        IndicatorConfiguration.defaults());
        Instant upAsOf = uptrendBars.getLast().timestamp().plusSeconds(3600);

        TechnicalScoreResult firstRun =
                scoringEngine.score(
                        "UPTREND",
                        uptrendBars,
                        upIndicators,
                        ScoreWeights.defaults(),
                        upAsOf,
                        24L,
                        true,
                        true);
        TechnicalScoreResult repeatRun =
                scoringEngine.score(
                        "UPTREND",
                        uptrendBars,
                        upIndicators,
                        ScoreWeights.defaults(),
                        upAsOf,
                        24L,
                        true,
                        true);

        assertThat(firstRun).isEqualTo(repeatRun);
        assertThat(firstRun.status()).isEqualTo(ScoreStatus.READY);
        assertThat(firstRun.trendDirection()).isEqualTo(TrendDirection.BULLISH);
        assertThat(firstRun.finalScore())
                .isGreaterThan(new BigDecimal("70.00"))
                .isLessThanOrEqualTo(new BigDecimal("100.00"));
        assertThat(firstRun.explanation().headline())
                .doesNotContainIgnoringCase("buy")
                .doesNotContainIgnoringCase("guaranteed");
        assertThat(firstRun.explanation().disclaimer()).contains("öngörmez");

        List<OhlcvBar> downtrendBars = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            BigDecimal close = BigDecimal.valueOf(200L - i * 2L);
            downtrendBars.add(
                    bar(
                            i,
                            close.add(BigDecimal.ONE),
                            close.add(BigDecimal.TWO),
                            close.subtract(BigDecimal.ONE),
                            close,
                            1_000_000L + (i * 25_000L)));
        }
        TechnicalAnalysisResult downIndicators =
                indicatorEngine.calculate(
                        downtrendBars,
                        List.of(IndicatorType.values()),
                        IndicatorConfiguration.defaults());

        TechnicalScoreResult downScore =
                scoringEngine.score(
                        "DOWNTREND",
                        downtrendBars,
                        downIndicators,
                        ScoreWeights.defaults(),
                        downtrendBars.getLast().timestamp().plusSeconds(3600),
                        24L,
                        true,
                        true);

        assertThat(downScore.status()).isEqualTo(ScoreStatus.READY);
        assertThat(downScore.trendDirection()).isEqualTo(TrendDirection.BEARISH);
        assertThat(downScore.finalScore())
                .isGreaterThanOrEqualTo(BigDecimal.ZERO)
                .isLessThan(new BigDecimal("30.00"));

        List<OhlcvBar> flatBars = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            flatBars.add(
                    bar(
                            i,
                            new BigDecimal("100"),
                            new BigDecimal("100"),
                            new BigDecimal("100"),
                            new BigDecimal("100"),
                            1_000_000L));
        }
        TechnicalAnalysisResult flatIndicators =
                indicatorEngine.calculate(
                        flatBars,
                        List.of(IndicatorType.values()),
                        IndicatorConfiguration.defaults());

        TechnicalScoreResult flatScore =
                scoringEngine.score(
                        "FLAT",
                        flatBars,
                        flatIndicators,
                        ScoreWeights.defaults(),
                        flatBars.getLast().timestamp().plusSeconds(3600),
                        24L,
                        true,
                        true);

        assertThat(flatScore.status()).isEqualTo(ScoreStatus.READY);
        assertThat(flatScore.finalScore()).isEqualByComparingTo("50.00");
        assertThat(flatScore.regime()).isEqualTo(ScoreRegime.NEUTRAL_TECHNICAL);
        assertThat(flatScore.trendDirection()).isEqualTo(TrendDirection.NEUTRAL);
    }

    @Test
    void returnsInsufficientDataForShortHistoryZeroVolumeAndStaleInputs() {
        List<OhlcvBar> shortBars = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            shortBars.add(
                    bar(
                            i,
                            new BigDecimal("100"),
                            new BigDecimal("102"),
                            new BigDecimal("99"),
                            new BigDecimal("101"),
                            1_000_000L));
        }
        TechnicalAnalysisResult shortIndicators =
                indicatorEngine.calculate(
                        shortBars,
                        List.of(IndicatorType.values()),
                        IndicatorConfiguration.defaults());

        TechnicalScoreResult shortResult =
                scoringEngine.score(
                        "SHORT",
                        shortBars,
                        shortIndicators,
                        ScoreWeights.defaults(),
                        shortBars.getLast().timestamp().plusSeconds(3600),
                        24L,
                        true,
                        true);

        assertThat(shortResult.status()).isEqualTo(ScoreStatus.INSUFFICIENT_DATA);
        assertThat(shortResult.finalScore()).isNull();
        assertThat(shortResult.regime()).isEqualTo(ScoreRegime.INSUFFICIENT_DATA);
        assertThat(shortResult.insufficientDataReasons()).isNotEmpty();

        List<OhlcvBar> zeroVolBars = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            zeroVolBars.add(
                    bar(
                            i,
                            BigDecimal.valueOf(100 + i),
                            BigDecimal.valueOf(102 + i),
                            BigDecimal.valueOf(99 + i),
                            BigDecimal.valueOf(101 + i),
                            0L));
        }
        TechnicalAnalysisResult zeroVolIndicators =
                indicatorEngine.calculate(
                        zeroVolBars,
                        List.of(IndicatorType.values()),
                        IndicatorConfiguration.defaults());

        TechnicalScoreResult zeroVolResult =
                scoringEngine.score(
                        "ZEROVOL",
                        zeroVolBars,
                        zeroVolIndicators,
                        ScoreWeights.defaults(),
                        zeroVolBars.getLast().timestamp().plusSeconds(3600),
                        24L,
                        true,
                        true);

        assertThat(zeroVolResult.status()).isEqualTo(ScoreStatus.INSUFFICIENT_DATA);
        assertThat(zeroVolResult.finalScore()).isNull();
        assertThat(zeroVolResult.components())
                .anyMatch(
                        c ->
                                c.type() == ScoreComponentType.VOLUME
                                        && c.status() == IndicatorStatus.INSUFFICIENT_DATA);

        TechnicalAnalysisResult staleIndicators =
                indicatorEngine.calculate(
                        shortBarsWithPositiveVolume45(),
                        List.of(IndicatorType.values()),
                        IndicatorConfiguration.defaults());
        List<OhlcvBar> validBars = shortBarsWithPositiveVolume45();
        TechnicalScoreResult staleResult =
                scoringEngine.score(
                        "STALE",
                        validBars,
                        staleIndicators,
                        ScoreWeights.defaults(),
                        validBars.getLast().timestamp().plusSeconds(30L * 3600L),
                        24L,
                        true,
                        true);

        assertThat(staleResult.staleData()).isTrue();
        assertThat(staleResult.status()).isEqualTo(ScoreStatus.INSUFFICIENT_DATA);
        assertThat(staleResult.finalScore()).isNull();
        assertThat(staleResult.insufficientDataReasons()).anyMatch(r -> r.contains("bayat"));
    }

    private static List<OhlcvBar> shortBarsWithPositiveVolume45() {
        List<OhlcvBar> list = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            list.add(
                    bar(
                            i,
                            BigDecimal.valueOf(100 + i),
                            BigDecimal.valueOf(102 + i),
                            BigDecimal.valueOf(99 + i),
                            BigDecimal.valueOf(101 + i),
                            500_000L));
        }
        return list;
    }

    private static OhlcvBar bar(
            int dayOffset,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            long volume) {
        Instant ts = T0.plusSeconds(86400L * dayOffset);
        return new OhlcvBar(
                ts,
                open,
                high,
                low,
                close,
                volume,
                "TRY",
                BarInterval.ONE_DAY,
                "MOCK_SIMULATED",
                false,
                true,
                "Europe/Istanbul",
                ts);
    }
}
