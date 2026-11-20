package com.stockvision.technicalanalysis.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.marketdata.domain.OhlcvBar;
import com.stockvision.marketdata.infrastructure.MockMarketDataProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicTechnicalIndicatorEngineTest {

    private static final Instant T0 = Instant.parse("2026-10-01T15:00:00Z");
    private final DeterministicTechnicalIndicatorEngine engine =
            new DeterministicTechnicalIndicatorEngine();

    @Test
    void matchesHandCalculatedSmaAndEmaFixtures() {
        List<OhlcvBar> bars = barsFromCloses("10", "11", "12", "13", "14");
        IndicatorConfiguration config =
                new IndicatorConfiguration(
                        3,
                        3,
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

        TechnicalAnalysisResult result =
                engine.calculate(bars, List.of(IndicatorType.SMA, IndicatorType.EMA), config);

        assertThat(result.engineVersion())
                .isEqualTo(DeterministicTechnicalIndicatorEngine.ENGINE_VERSION);
        assertThat(result.status()).isEqualTo(AnalysisRunStatus.READY);

        IndicatorSeries sma = result.series().get(0);
        assertThat(sma.warmUpBars()).isEqualTo(2);
        assertThat(sma.requiredBars()).isEqualTo(3);
        assertThat(sma.points().get(0).warmUp()).isTrue();
        assertThat(sma.points().get(0).values().get("value")).isNull();
        assertThat(sma.points().get(1).values().get("value")).isNull();
        assertThat(sma.points().get(2).warmUp()).isFalse();
        assertThat(sma.points().get(2).values().get("value")).isEqualByComparingTo("11.000000");
        assertThat(sma.points().get(3).values().get("value")).isEqualByComparingTo("12.000000");
        assertThat(sma.points().get(4).values().get("value")).isEqualByComparingTo("13.000000");

        IndicatorSeries ema = result.series().get(1);
        assertThat(ema.points().get(1).values().get("value")).isNull();
        assertThat(ema.points().get(2).values().get("value")).isEqualByComparingTo("11.000000");
        assertThat(ema.points().get(3).values().get("value")).isEqualByComparingTo("12.000000");
        assertThat(ema.points().get(4).values().get("value")).isEqualByComparingTo("13.000000");
    }

    @Test
    void matchesHandCalculatedRsiWilderFixturesAndExtremeTrends() {
        IndicatorConfiguration config = configWithRsiPeriod(3);

        List<OhlcvBar> mixedBars = barsFromCloses("10", "11", "12", "11", "13");
        IndicatorSeries mixedRsi =
                engine.calculate(mixedBars, List.of(IndicatorType.RSI), config).series().getFirst();
        assertThat(mixedRsi.status()).isEqualTo(IndicatorStatus.READY);
        assertThat(mixedRsi.warmUpBars()).isEqualTo(3);
        assertThat(mixedRsi.requiredBars()).isEqualTo(4);
        assertThat(mixedRsi.points().get(2).warmUp()).isTrue();
        assertThat(mixedRsi.points().get(2).values().get("value")).isNull();
        assertThat(mixedRsi.points().get(3).values().get("value"))
                .isEqualByComparingTo("66.666667");
        assertThat(mixedRsi.points().get(4).values().get("value"))
                .isEqualByComparingTo("83.333333");

        List<OhlcvBar> pureGainBars = barsFromCloses("10", "11", "12", "13");
        IndicatorSeries pureGainRsi =
                engine.calculate(pureGainBars, List.of(IndicatorType.RSI), config)
                        .series()
                        .getFirst();
        assertThat(pureGainRsi.latestValues().get("value")).isEqualByComparingTo("100.000000");

        List<OhlcvBar> pureLossBars = barsFromCloses("13", "12", "11", "10");
        IndicatorSeries pureLossRsi =
                engine.calculate(pureLossBars, List.of(IndicatorType.RSI), config)
                        .series()
                        .getFirst();
        assertThat(pureLossRsi.latestValues().get("value")).isEqualByComparingTo("0.000000");
    }

    @Test
    void matchesHandCalculatedMacdBollingerStochasticAtrAndVolumeFixtures() {
        IndicatorConfiguration config =
                new IndicatorConfiguration(
                        3,
                        3,
                        3,
                        3,
                        5,
                        2,
                        3,
                        new BigDecimal("2.0"),
                        3,
                        2,
                        3,
                        3);

        List<OhlcvBar> macdBars = barsFromCloses("10", "12", "14", "16", "18", "20", "23");
        IndicatorSeries macd =
                engine.calculate(macdBars, List.of(IndicatorType.MACD), config).series().getFirst();
        assertThat(macd.warmUpBars()).isEqualTo(5);
        assertThat(macd.requiredBars()).isEqualTo(6);
        assertThat(macd.points().get(4).warmUp()).isTrue();
        assertThat(macd.points().get(4).values().get("macd")).isEqualByComparingTo("2.000000");
        assertThat(macd.points().get(4).values().get("signal")).isNull();
        assertThat(macd.points().get(5).warmUp()).isFalse();
        assertThat(macd.points().get(5).values().get("signal")).isEqualByComparingTo("2.000000");
        assertThat(macd.points().get(5).values().get("histogram")).isEqualByComparingTo("0.000000");
        assertThat(macd.points().get(6).values().get("macd")).isEqualByComparingTo("2.166667");
        assertThat(macd.points().get(6).values().get("signal")).isEqualByComparingTo("2.111111");
        assertThat(macd.points().get(6).values().get("histogram")).isEqualByComparingTo("0.055556");

        List<OhlcvBar> bbBars = barsFromCloses("10", "12", "14");
        IndicatorSeries bb =
                engine.calculate(bbBars, List.of(IndicatorType.BOLLINGER), config)
                        .series()
                        .getFirst();
        assertThat(bb.latestValues().get("middle")).isEqualByComparingTo("12.000000");
        assertThat(bb.latestValues().get("upper")).isEqualByComparingTo("15.265986");
        assertThat(bb.latestValues().get("lower")).isEqualByComparingTo("8.734014");
        assertThat(bb.latestValues().get("bandwidth")).isEqualByComparingTo("0.544331");

        List<OhlcvBar> stochBars =
                List.of(
                        bar(0, "10", "12", "8", "10", 100L),
                        bar(1, "11", "14", "9", "13", 200L),
                        bar(2, "13", "16", "10", "14", 300L),
                        bar(3, "14", "18", "10", "18", 600L));
        IndicatorSeries stoch =
                engine.calculate(stochBars, List.of(IndicatorType.STOCHASTIC), config)
                        .series()
                        .getFirst();
        assertThat(stoch.points().get(2).warmUp()).isTrue();
        assertThat(stoch.points().get(2).values().get("k")).isEqualByComparingTo("75.000000");
        assertThat(stoch.points().get(2).values().get("d")).isNull();
        assertThat(stoch.points().get(3).warmUp()).isFalse();
        assertThat(stoch.points().get(3).values().get("k")).isEqualByComparingTo("100.000000");
        assertThat(stoch.points().get(3).values().get("d")).isEqualByComparingTo("87.500000");

        List<OhlcvBar> atrBars =
                List.of(
                        bar(0, "10", "12", "8", "10", 100L),
                        bar(1, "10", "13", "9", "11", 100L),
                        bar(2, "14", "16", "14", "15", 100L),
                        bar(3, "14", "15", "12", "13", 100L),
                        bar(4, "14", "19", "13", "18", 100L));
        IndicatorSeries atr =
                engine.calculate(atrBars, List.of(IndicatorType.ATR), config).series().getFirst();
        assertThat(atr.points().get(2).warmUp()).isTrue();
        assertThat(atr.points().get(3).values().get("value")).isEqualByComparingTo("4.000000");
        assertThat(atr.points().get(4).values().get("value")).isEqualByComparingTo("4.666667");

        IndicatorSeries volumeAvg =
                engine.calculate(stochBars, List.of(IndicatorType.VOLUME_AVG), config)
                        .series()
                        .getFirst();
        assertThat(volumeAvg.points().get(2).values().get("average"))
                .isEqualByComparingTo("200.000000");
        assertThat(volumeAvg.points().get(2).values().get("ratio"))
                .isEqualByComparingTo("1.500000");
        assertThat(volumeAvg.points().get(3).values().get("average"))
                .isEqualByComparingTo("366.666667");
        assertThat(volumeAvg.points().get(3).values().get("ratio"))
                .isEqualByComparingTo("1.636364");
    }

    @Test
    void handlesFlatPricesZeroVolumeEmptyAndShortHistorySafely() {
        List<OhlcvBar> flatZeroVolBars = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            flatZeroVolBars.add(bar(i, "100", "100", "100", "100", 0L));
        }

        TechnicalAnalysisResult flatResult =
                engine.calculate(
                        flatZeroVolBars,
                        List.of(IndicatorType.values()),
                        IndicatorConfiguration.defaults());

        assertThat(flatResult.status()).isEqualTo(AnalysisRunStatus.READY);
        for (IndicatorSeries series : flatResult.series()) {
            switch (series.type()) {
                case SMA, EMA -> assertThat(series.latestValues().get("value"))
                        .isEqualByComparingTo("100.000000");
                case RSI -> assertThat(series.latestValues().get("value"))
                        .isEqualByComparingTo("50.000000");
                case MACD -> {
                    assertThat(series.latestValues().get("macd")).isEqualByComparingTo("0.000000");
                    assertThat(series.latestValues().get("signal"))
                            .isEqualByComparingTo("0.000000");
                    assertThat(series.latestValues().get("histogram"))
                            .isEqualByComparingTo("0.000000");
                }
                case BOLLINGER -> {
                    assertThat(series.latestValues().get("upper"))
                            .isEqualByComparingTo("100.000000");
                    assertThat(series.latestValues().get("middle"))
                            .isEqualByComparingTo("100.000000");
                    assertThat(series.latestValues().get("lower"))
                            .isEqualByComparingTo("100.000000");
                    assertThat(series.latestValues().get("bandwidth"))
                            .isEqualByComparingTo("0.000000");
                }
                case STOCHASTIC -> {
                    assertThat(series.latestValues().get("k")).isEqualByComparingTo("50.000000");
                    assertThat(series.latestValues().get("d")).isEqualByComparingTo("50.000000");
                }
                case ATR -> assertThat(series.latestValues().get("value"))
                        .isEqualByComparingTo("0.000000");
                case VOLUME_AVG -> {
                    assertThat(series.latestValues().get("average"))
                            .isEqualByComparingTo("0.000000");
                    assertThat(series.latestValues().get("ratio")).isNull();
                }
            }
        }

        TechnicalAnalysisResult shortHistory =
                engine.calculate(
                        flatZeroVolBars.subList(0, 5),
                        List.of(IndicatorType.values()),
                        IndicatorConfiguration.defaults());
        assertThat(shortHistory.status()).isEqualTo(AnalysisRunStatus.INSUFFICIENT_DATA);
        assertThat(shortHistory.series())
                .allMatch(
                        s ->
                                s.status() == IndicatorStatus.INSUFFICIENT_DATA
                                        && s.latestValues().isEmpty());

        TechnicalAnalysisResult emptyHistory =
                engine.calculate(
                        List.of(),
                        List.of(IndicatorType.RSI, IndicatorType.MACD),
                        IndicatorConfiguration.defaults());
        assertThat(emptyHistory.status()).isEqualTo(AnalysisRunStatus.INSUFFICIENT_DATA);
        assertThat(emptyHistory.barCount()).isZero();
    }

    @Test
    void guaranteesNoLookAheadBiasAcrossPrefixSubslices() {
        MockMarketDataProvider mockProvider =
                new MockMarketDataProvider(
                        Clock.fixed(Instant.parse("2026-11-18T18:00:00Z"), ZoneOffset.UTC));
        List<OhlcvBar> fullBars =
                mockProvider.getHistoricalBars(
                        "THYAO",
                        BarInterval.ONE_DAY,
                        Instant.parse("2026-06-01T00:00:00Z"),
                        Instant.parse("2026-11-18T18:00:00Z"),
                        false);
        assertThat(fullBars.size()).isGreaterThan(50);

        IndicatorConfiguration config = IndicatorConfiguration.defaults();
        TechnicalAnalysisResult fullResult =
                engine.calculate(fullBars, List.of(IndicatorType.values()), config);

        for (int prefixEnd : new int[] {20, 35, 45}) {
            List<OhlcvBar> prefixBars = fullBars.subList(0, prefixEnd);
            TechnicalAnalysisResult prefixResult =
                    engine.calculate(prefixBars, List.of(IndicatorType.values()), config);

            for (int s = 0; s < fullResult.series().size(); s++) {
                IndicatorSeries fullSeries = fullResult.series().get(s);
                IndicatorSeries prefixSeries = prefixResult.series().get(s);
                for (int i = 0; i < prefixEnd; i++) {
                    assertThat(prefixSeries.points().get(i))
                            .isEqualTo(fullSeries.points().get(i));
                }
            }
        }
    }

    @Test
    void rejectsUnorderedDuplicateOrMixedBarsAndInvalidConfiguration() {
        OhlcvBar b1 = bar(0, "10", "11", "9", "10", 100L);
        OhlcvBar b2 = bar(1, "10", "12", "9", "11", 100L);
        OhlcvBar duplicateTimestamp = bar(0, "11", "12", "10", "11", 100L);
        OhlcvBar adjustedBar =
                new OhlcvBar(
                        T0.plusSeconds(86400L * 2),
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        100L,
                        "TRY",
                        BarInterval.ONE_DAY,
                        "MOCK_SIMULATED",
                        true,
                        true,
                        "Europe/Istanbul",
                        T0);

        IndicatorConfiguration defaults = IndicatorConfiguration.defaults();

        assertThatThrownBy(
                        () ->
                                engine.calculate(
                                        List.of(b2, b1), List.of(IndicatorType.SMA), defaults))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly ascending");

        assertThatThrownBy(
                        () ->
                                engine.calculate(
                                        List.of(b1, duplicateTimestamp),
                                        List.of(IndicatorType.SMA),
                                        defaults))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate-free");

        assertThatThrownBy(
                        () ->
                                engine.calculate(
                                        List.of(b1, b2, adjustedBar),
                                        List.of(IndicatorType.SMA),
                                        defaults))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("adjustment");

        assertThatThrownBy(
                        () ->
                                new IndicatorConfiguration(
                                        20,
                                        20,
                                        14,
                                        26,
                                        12,
                                        9,
                                        20,
                                        new BigDecimal("2.0"),
                                        14,
                                        3,
                                        14,
                                        20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("macdFastPeriod");
    }

    private static IndicatorConfiguration configWithRsiPeriod(int rsiPeriod) {
        return new IndicatorConfiguration(
                20, 20, rsiPeriod, 12, 26, 9, 20, new BigDecimal("2.0"), 14, 3, 14, 20);
    }

    private static List<OhlcvBar> barsFromCloses(String... closes) {
        List<OhlcvBar> bars = new ArrayList<>(closes.length);
        for (int i = 0; i < closes.length; i++) {
            bars.add(bar(i, closes[i], closes[i], closes[i], closes[i], 1000L));
        }
        return bars;
    }

    private static OhlcvBar bar(
            int dayOffset, String open, String high, String low, String close, long volume) {
        Instant ts = T0.plusSeconds(86400L * dayOffset);
        return new OhlcvBar(
                ts,
                new BigDecimal(open),
                new BigDecimal(high),
                new BigDecimal(low),
                new BigDecimal(close),
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
