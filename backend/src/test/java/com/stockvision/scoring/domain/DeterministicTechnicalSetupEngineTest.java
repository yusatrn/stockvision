package com.stockvision.scoring.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.marketdata.domain.LatestQuote;
import com.stockvision.marketdata.domain.OhlcvBar;
import com.stockvision.technicalanalysis.domain.DeterministicTechnicalIndicatorEngine;
import com.stockvision.technicalanalysis.domain.IndicatorConfiguration;
import com.stockvision.technicalanalysis.domain.IndicatorType;
import com.stockvision.technicalanalysis.domain.TechnicalAnalysisResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicTechnicalSetupEngineTest {

    private static final Instant T0 = Instant.parse("2026-10-01T15:00:00Z");

    private final DeterministicTechnicalIndicatorEngine indicatorEngine =
            new DeterministicTechnicalIndicatorEngine();
    private final DeterministicTechnicalScoringEngine scoringEngine =
            new DeterministicTechnicalScoringEngine();
    private final DeterministicTechnicalSetupEngine setupEngine =
            new DeterministicTechnicalSetupEngine();

    @Test
    void detectsHighMomentumBreakoutWithOrderedPriceLadderAndBistMargin() {
        List<OhlcvBar> bars = new ArrayList<>();
        double[] wave = {
            100, 101, 99.5, 101.5, 100.5, 102, 100, 102.5, 101, 103,
            101.5, 103.5, 102, 104, 102.5, 104.5, 103, 104.5, 103.5, 105,
            103.5, 105, 104, 105.5, 104, 105.5, 104.5, 106, 104.5, 106,
            105, 106.5, 105, 106.5, 105.5, 107, 106, 107, 106.5, 107.5,
            108, 108.5, 109, 109.5, 111.5
        };
        for (int i = 0; i < wave.length; i++) {
            BigDecimal close = BigDecimal.valueOf(wave[i]);
            long vol = i == wave.length - 1 ? 2_600_000L : 1_000_000L;
            bars.add(
                    bar(
                            i,
                            close.subtract(new BigDecimal("0.60")),
                            close.add(new BigDecimal("0.80")),
                            close.subtract(new BigDecimal("1.20")),
                            close,
                            vol));
        }

        LatestQuote quote =
                new LatestQuote(
                        "THYAO",
                        new BigDecimal("111.50"),
                        new BigDecimal("100.00"),
                        new BigDecimal("11.50"),
                        new BigDecimal("11.5000"),
                        2_600_000L,
                        "TRY",
                        bars.getLast().timestamp(),
                        "MOCK",
                        true,
                        0,
                        "Europe/Istanbul");

        TechnicalSetupResult result = evaluate("THYAO", bars, quote, false);

        assertThat(result.status()).isEqualTo(ScoreStatus.READY);
        assertThat(result.category()).isEqualTo(SetupCategory.HIGH_MOMENTUM_BREAKOUT);
        assertThat(result.breakoutConfirmed()).isTrue();
        assertThat(result.momentumBreakoutScore()).isGreaterThanOrEqualTo(new BigDecimal("62.00"));

        assertPriceLadderInvariants(result);
        assertThat(result.riskReward().riskRewardToR1()).isPositive();
        assertThat(result.riskReward().riskRewardToR2())
                .isGreaterThan(result.riskReward().riskRewardToR1());

        assertThat(result.bistMargin()).isNotNull();
        assertThat(result.bistMargin().theoreticalCeilingPrice())
                .isEqualByComparingTo("110.0000");
        assertThat(result.bistMargin().theoreticalFloorPrice())
                .isEqualByComparingTo("90.0000");
        assertThat(result.bistMargin().note()).contains("tahmini değildir");
        assertNoProhibitedLanguage(result);
    }

    @Test
    void detectsOverextendedRiskOnParabolicRally() {
        List<OhlcvBar> bars = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            BigDecimal close = BigDecimal.valueOf(100L + i * 2L);
            bars.add(
                    bar(
                            i,
                            close.subtract(BigDecimal.ONE),
                            close.add(BigDecimal.ONE),
                            close.subtract(BigDecimal.TWO),
                            close,
                            1_500_000L + i * 20_000L));
        }

        TechnicalSetupResult result = evaluate("RALLY", bars, null, true);

        assertThat(result.status()).isEqualTo(ScoreStatus.READY);
        assertThat(result.category()).isEqualTo(SetupCategory.OVEREXTENDED_RISK);
        assertThat(result.riskWarnings())
                .anySatisfy(w -> assertThat(w).contains("aşırı ısınma"));
        assertNoProhibitedLanguage(result);
    }

    @Test
    void detectsPullbackWatchNearSupportInPositiveRegime() {
        List<OhlcvBar> bars = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            BigDecimal close = BigDecimal.valueOf(100.0 + i * 1.1);
            bars.add(
                    bar(
                            i,
                            close.subtract(new BigDecimal("0.60")),
                            close.add(new BigDecimal("1.00")),
                            close.subtract(new BigDecimal("1.00")),
                            close,
                            1_200_000L));
        }
        double[] pullback = {138.0, 136.5, 135.0, 133.5, 132.5, 131.5, 131.0, 130.8, 131.2};
        for (int j = 0; j < pullback.length; j++) {
            BigDecimal close = BigDecimal.valueOf(pullback[j]);
            bars.add(
                    bar(
                            36 + j,
                            close.add(new BigDecimal("0.40")),
                            close.add(new BigDecimal("0.90")),
                            close.subtract(new BigDecimal("0.80")),
                            close,
                            950_000L));
        }

        TechnicalSetupResult result = evaluate("PULLBACK", bars, null, false);

        assertThat(result.status()).isEqualTo(ScoreStatus.READY);
        assertThat(result.category()).isEqualTo(SetupCategory.PULLBACK_WATCH);
        assertPriceLadderInvariants(result);
        assertNoProhibitedLanguage(result);
    }

    @Test
    void detectsWeakBearishTrendOnSustainedDecline() {
        List<OhlcvBar> bars = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            BigDecimal close = BigDecimal.valueOf(200L - i * 2L);
            bars.add(
                    bar(
                            i,
                            close.add(BigDecimal.ONE),
                            close.add(BigDecimal.TWO),
                            close.subtract(BigDecimal.ONE),
                            close,
                            1_200_000L));
        }

        TechnicalSetupResult result = evaluate("WEAK", bars, null, false);

        assertThat(result.status()).isEqualTo(ScoreStatus.READY);
        assertThat(result.category()).isEqualTo(SetupCategory.WEAK_BEARISH);
        assertPriceLadderInvariants(result);
        assertNoProhibitedLanguage(result);
    }

    @Test
    void returnsInsufficientDataOnShortOrStaleHistory() {
        List<OhlcvBar> shortBars = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            BigDecimal close = BigDecimal.valueOf(100L + i);
            shortBars.add(
                    bar(
                            i,
                            close.subtract(BigDecimal.ONE),
                            close.add(BigDecimal.ONE),
                            close.subtract(BigDecimal.ONE),
                            close,
                            500_000L));
        }

        TechnicalSetupResult shortResult = evaluate("SHORT", shortBars, null, true);

        assertThat(shortResult.status()).isEqualTo(ScoreStatus.INSUFFICIENT_DATA);
        assertThat(shortResult.category()).isEqualTo(SetupCategory.INSUFFICIENT_DATA);
        assertThat(shortResult.momentumBreakoutScore()).isNull();
        assertThat(shortResult.supportWatchZone()).isNull();
        assertThat(shortResult.resistanceExitZone()).isNull();
        assertThat(shortResult.invalidationRiskLevel()).isNull();
        assertThat(shortResult.insufficientDataReasons()).isNotEmpty();
        assertNoProhibitedLanguage(shortResult);

        List<OhlcvBar> fullBars = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            BigDecimal close = BigDecimal.valueOf(100L + i);
            fullBars.add(
                    bar(
                            i,
                            close.subtract(BigDecimal.ONE),
                            close.add(BigDecimal.ONE),
                            close.subtract(BigDecimal.TWO),
                            close,
                            900_000L));
        }
        TechnicalAnalysisResult indicators =
                indicatorEngine.calculate(
                        fullBars,
                        List.of(IndicatorType.values()),
                        IndicatorConfiguration.defaults());
        Instant staleAsOf = fullBars.getLast().timestamp().plus(72, ChronoUnit.HOURS);
        TechnicalScoreResult staleScore =
                scoringEngine.score(
                        "STALE",
                        fullBars,
                        indicators,
                        ScoreWeights.defaults(),
                        staleAsOf,
                        24L,
                        true,
                        false);
        TechnicalSetupResult staleResult =
                setupEngine.analyze("STALE", fullBars, indicators, staleScore, null, false);

        assertThat(staleResult.status()).isEqualTo(ScoreStatus.INSUFFICIENT_DATA);
        assertThat(staleResult.category()).isEqualTo(SetupCategory.INSUFFICIENT_DATA);
        assertThat(staleResult.staleData()).isTrue();
    }

    private TechnicalSetupResult evaluate(
            String symbol, List<OhlcvBar> bars, LatestQuote quote, boolean simulated) {
        TechnicalAnalysisResult indicators =
                indicatorEngine.calculate(
                        bars,
                        List.of(IndicatorType.values()),
                        IndicatorConfiguration.defaults());
        TechnicalScoreResult score =
                scoringEngine.score(
                        symbol,
                        bars,
                        indicators,
                        ScoreWeights.defaults(),
                        bars.getLast().timestamp().plus(1, ChronoUnit.HOURS),
                        24L,
                        true,
                        simulated);
        return setupEngine.analyze(symbol, bars, indicators, score, quote, simulated);
    }

    private static void assertPriceLadderInvariants(TechnicalSetupResult result) {
        BigDecimal price = result.technicalReferencePrice();
        assertThat(price).isNotNull().isPositive();
        assertThat(result.supportWatchZone()).isNotNull();
        assertThat(result.resistanceExitZone()).isNotNull();
        assertThat(result.invalidationRiskLevel()).isNotNull().isPositive();

        assertThat(result.invalidationRiskLevel()).isLessThan(price);
        assertThat(result.supportWatchZone().low())
                .isPositive()
                .isLessThanOrEqualTo(result.supportWatchZone().high());
        assertThat(result.supportWatchZone().high()).isLessThanOrEqualTo(price);
        assertThat(result.resistanceExitZone().low()).isGreaterThan(price);
        assertThat(result.resistanceExitZone().high())
                .isGreaterThan(result.resistanceExitZone().low());
    }

    private static void assertNoProhibitedLanguage(TechnicalSetupResult result) {
        List<String> texts = new ArrayList<>();
        texts.add(result.headline());
        texts.add(result.disclaimer());
        texts.addAll(result.rationaleItems());
        texts.addAll(result.riskWarnings());
        for (String text : texts) {
            assertThat(text)
                    .doesNotContainIgnoringCase("tavan olacak")
                    .doesNotContainIgnoringCase("garanti")
                    .doesNotContainIgnoringCase("risksiz")
                    .doesNotContainIgnoringCase("hemen al")
                    .doesNotContainIgnoringCase("hemen sat");
        }
    }

    private static OhlcvBar bar(
            int index,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            long volume) {
        Instant ts = T0.plus(index, ChronoUnit.DAYS);
        return new OhlcvBar(
                ts,
                open,
                high,
                low,
                close,
                volume,
                "TRY",
                BarInterval.ONE_DAY,
                "MOCK",
                false,
                true,
                "Europe/Istanbul",
                ts);
    }
}
