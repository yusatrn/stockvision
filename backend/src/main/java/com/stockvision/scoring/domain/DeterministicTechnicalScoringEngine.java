package com.stockvision.scoring.domain;

import com.stockvision.common.domain.FinancialMath;
import com.stockvision.marketdata.domain.OhlcvBar;
import com.stockvision.technicalanalysis.domain.IndicatorPoint;
import com.stockvision.technicalanalysis.domain.IndicatorSeries;
import com.stockvision.technicalanalysis.domain.IndicatorStatus;
import com.stockvision.technicalanalysis.domain.IndicatorType;
import com.stockvision.technicalanalysis.domain.TechnicalAnalysisResult;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure {@link BigDecimal} explainable scoring engine mapping 7 technical components in [-100, +100]
 * into a weighted 0–100 descriptive technical score.
 *
 * <p>Missing, zero-volume, or stale inputs are explicitly rejected as {@link ScoreStatus#INSUFFICIENT_DATA}
 * and never silently defaulted to neutral.
 */
public class DeterministicTechnicalScoringEngine implements TechnicalScoringService {

    public static final String ALGORITHM_VERSION = "score-engine-v1.0.0";

    private static final MathContext MC = FinancialMath.MATH_CONTEXT;
    private static final BigDecimal NEGATIVE_HUNDRED = new BigDecimal("-100.00");
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal TWO = BigDecimal.valueOf(2L);
    private static final BigDecimal FIFTY = new BigDecimal("50.00");
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal TWO_HUNDRED = new BigDecimal("200.00");

    private static final BigDecimal BULLISH_TREND_THRESHOLD = new BigDecimal("15.00");
    private static final BigDecimal BEARISH_TREND_THRESHOLD = new BigDecimal("-15.00");
    private static final BigDecimal THIRTY = new BigDecimal("30");
    private static final BigDecimal SEVENTY = new BigDecimal("70");
    private static final BigDecimal EIGHTY = new BigDecimal("80");
    private static final BigDecimal NEG_EIGHTY = new BigDecimal("-80");

    private static final String DISCLAIMER =
            "Teknik skorlar geçmiş veya simüle indikatörlerin betimsel matematiksel özetleridir. "
                    + "Gelecekteki getiriyi öngörmez, kazanç vaadi içermez ve al/sat yatırım danışmanlığı niteliği taşımaz.";

    @Override
    public String algorithmVersion() {
        return ALGORITHM_VERSION;
    }

    @Override
    public TechnicalScoreResult score(
            String symbol,
            List<OhlcvBar> orderedBars,
            TechnicalAnalysisResult indicatorResult,
            ScoreWeights weights,
            Instant asOf,
            long maxDataAgeHours,
            boolean rejectStaleData,
            boolean simulated) {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(orderedBars, "orderedBars must not be null");
        Objects.requireNonNull(indicatorResult, "indicatorResult must not be null");
        Objects.requireNonNull(weights, "weights must not be null");
        Objects.requireNonNull(asOf, "asOf must not be null");
        if (maxDataAgeHours < 1L) {
            throw new IllegalArgumentException("maxDataAgeHours must be >= 1");
        }

        Map<IndicatorType, IndicatorSeries> seriesMap = new EnumMap<>(IndicatorType.class);
        for (IndicatorSeries series : indicatorResult.series()) {
            seriesMap.put(series.type(), series);
        }

        BigDecimal latestClose =
                orderedBars.isEmpty() ? null : orderedBars.getLast().close();
        BigDecimal latestRsi = extractLatestRsi(seriesMap);

        boolean staleData = isDataStale(orderedBars, asOf, maxDataAgeHours);
        List<String> insufficientReasons = new ArrayList<>();

        if (orderedBars.isEmpty()) {
            insufficientReasons.add("Skorlama için OHLCV fiyat barı bulunamadı.");
        } else if (staleData && rejectStaleData) {
            long ageHours = Duration.between(orderedBars.getLast().timestamp(), asOf).toHours();
            insufficientReasons.add(
                    "Son fiyat barı bayat ("
                            + ageHours
                            + " saat önce; izin verilen en fazla yaş "
                            + maxDataAgeHours
                            + " saattir).");
        }

        ScoreComponentResult trend = evaluateTrend(orderedBars, seriesMap, weights);
        ScoreComponentResult rsi = evaluateRsi(seriesMap, weights);
        ScoreComponentResult macd = evaluateMacd(orderedBars, seriesMap, weights);
        ScoreComponentResult volume = evaluateVolume(orderedBars, seriesMap, weights);
        ScoreComponentResult bollinger = evaluateBollinger(orderedBars, seriesMap, weights);
        ScoreComponentResult momentum = evaluateMomentum(orderedBars, seriesMap, weights);
        ScoreComponentResult supportResistance = evaluateSupportResistance(orderedBars, weights);

        List<ScoreComponentResult> components =
                List.of(trend, rsi, macd, volume, bollinger, momentum, supportResistance);

        for (ScoreComponentResult component : components) {
            if (component.status() == IndicatorStatus.INSUFFICIENT_DATA) {
                insufficientReasons.add(
                        component.type().name() + ": " + component.explanation());
            }
        }

        TrendDirection trendDirection = deriveTrendDirection(trend);

        boolean hasSufficiencyFailure =
                orderedBars.isEmpty()
                        || (staleData && rejectStaleData)
                        || components.stream()
                                .anyMatch(c -> c.status() == IndicatorStatus.INSUFFICIENT_DATA);

        if (hasSufficiencyFailure) {
            ScoreExplanation explanation =
                    buildInsufficientExplanation(
                            symbol, insufficientReasons, staleData, simulated);
            return new TechnicalScoreResult(
                    ALGORITHM_VERSION,
                    indicatorResult.engineVersion(),
                    ScoreStatus.INSUFFICIENT_DATA,
                    null,
                    ScoreRegime.INSUFFICIENT_DATA,
                    trendDirection,
                    latestClose,
                    latestRsi,
                    staleData,
                    insufficientReasons,
                    weights,
                    components,
                    explanation);
        }

        BigDecimal weightedSignal = BigDecimal.ZERO;
        for (ScoreComponentResult component : components) {
            weightedSignal =
                    weightedSignal.add(
                            component.normalizedScore().multiply(component.weightPercent(), MC),
                            MC);
        }
        weightedSignal = weightedSignal.divide(HUNDRED, MC);

        BigDecimal rawFinalScore = weightedSignal.add(HUNDRED, MC).divide(TWO, MC);
        BigDecimal finalScore = scaleScore(clamp(rawFinalScore, ZERO, HUNDRED));
        ScoreRegime regime = ScoreRegime.fromScore(finalScore);
        ScoreExplanation explanation =
                buildReadyExplanation(
                        symbol, finalScore, regime, trendDirection, components, staleData, simulated);

        return new TechnicalScoreResult(
                ALGORITHM_VERSION,
                indicatorResult.engineVersion(),
                ScoreStatus.READY,
                finalScore,
                regime,
                trendDirection,
                latestClose,
                latestRsi,
                staleData,
                List.of(),
                weights,
                components,
                explanation);
    }

    private ScoreComponentResult evaluateTrend(
            List<OhlcvBar> bars,
            Map<IndicatorType, IndicatorSeries> seriesMap,
            ScoreWeights weights) {
        BigDecimal weight = weights.weightOf(ScoreComponentType.TREND);
        IndicatorSeries sma = seriesMap.get(IndicatorType.SMA);
        IndicatorSeries ema = seriesMap.get(IndicatorType.EMA);

        if (bars.size() < 25
                || sma == null
                || sma.status() != IndicatorStatus.READY
                || ema == null
                || ema.status() != IndicatorStatus.READY) {
            return insufficientComponent(
                    ScoreComponentType.TREND,
                    weight,
                    "Hazır SMA ve EMA serileriyle birlikte en az 25 sıralı bar gerektirir.",
                    Map.of());
        }

        BigDecimal close = bars.getLast().close();
        BigDecimal smaVal = sma.latestValues().get("value");
        BigDecimal emaVal = ema.latestValues().get("value");
        BigDecimal emaPrev5 = ema.points().get(ema.points().size() - 5).values().get("value");

        if (smaVal == null
                || emaVal == null
                || emaPrev5 == null
                || smaVal.compareTo(BigDecimal.ZERO) <= 0
                || emaVal.compareTo(BigDecimal.ZERO) <= 0
                || emaPrev5.compareTo(BigDecimal.ZERO) <= 0) {
            return insufficientComponent(
                    ScoreComponentType.TREND,
                    weight,
                    "SMA/EMA trend referans değerleri eksik veya pozitif değil.",
                    Map.of());
        }

        BigDecimal closeVsSmaPct =
                close.subtract(smaVal, MC).divide(smaVal, MC).multiply(HUNDRED, MC);
        BigDecimal closeVsEmaPct =
                close.subtract(emaVal, MC).divide(emaVal, MC).multiply(HUNDRED, MC);
        BigDecimal emaSlopePct =
                emaVal.subtract(emaPrev5, MC).divide(emaPrev5, MC).multiply(HUNDRED, MC);

        BigDecimal smaPart =
                clamp(closeVsSmaPct.multiply(BigDecimal.valueOf(8L), MC), new BigDecimal("-40"), new BigDecimal("40"));
        BigDecimal emaPart =
                clamp(closeVsEmaPct.multiply(BigDecimal.valueOf(8L), MC), new BigDecimal("-30"), new BigDecimal("30"));
        BigDecimal slopePart =
                clamp(emaSlopePct.multiply(BigDecimal.valueOf(15L), MC), new BigDecimal("-30"), new BigDecimal("30"));

        BigDecimal score = scaleScore(clamp(smaPart.add(emaPart, MC).add(slopePart, MC), NEGATIVE_HUNDRED, HUNDRED));

        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        metrics.put("close", close);
        metrics.put("sma", smaVal);
        metrics.put("ema", emaVal);
        metrics.put("emaSlope5BarPct", FinancialMath.scalePercentage(emaSlopePct));

        String rationale =
                "Kapanış ("
                        + close
                        + ") / SMA ("
                        + smaVal
                        + ") ve EMA ("
                        + emaVal
                        + "); 5 barlık EMA eğimi %"
                        + FinancialMath.scalePercentage(emaSlopePct)
                        + ".";
        return readyComponent(ScoreComponentType.TREND, weight, score, rationale, metrics);
    }

    private ScoreComponentResult evaluateRsi(
            Map<IndicatorType, IndicatorSeries> seriesMap, ScoreWeights weights) {
        BigDecimal weight = weights.weightOf(ScoreComponentType.RSI);
        IndicatorSeries rsiSeries = seriesMap.get(IndicatorType.RSI);
        if (rsiSeries == null || rsiSeries.status() != IndicatorStatus.READY) {
            return insufficientComponent(
                    ScoreComponentType.RSI,
                    weight,
                    "Hazır 14 periyotluk Wilder RSI serisi gerektirir.",
                    Map.of());
        }
        BigDecimal rsi = rsiSeries.latestValues().get("value");
        if (rsi == null) {
            return insufficientComponent(
                    ScoreComponentType.RSI,
                    weight,
                    "Son RSI değeri kullanılamıyor.",
                    Map.of());
        }

        BigDecimal score;
        if (rsi.compareTo(FIFTY) >= 0 && rsi.compareTo(SEVENTY) <= 0) {
            score = rsi.subtract(FIFTY, MC).multiply(BigDecimal.valueOf(4L), MC);
        } else if (rsi.compareTo(SEVENTY) > 0) {
            score = EIGHTY.subtract(rsi.subtract(SEVENTY, MC).multiply(TWO, MC), MC);
        } else if (rsi.compareTo(THIRTY) >= 0) {
            score = rsi.subtract(FIFTY, MC).multiply(BigDecimal.valueOf(4L), MC);
        } else {
            score = NEG_EIGHTY.add(THIRTY.subtract(rsi, MC).multiply(TWO, MC), MC);
        }
        score = scaleScore(clamp(score, NEGATIVE_HUNDRED, HUNDRED));

        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        metrics.put("rsi", rsi);

        String rationale =
                "Wilder RSI ("
                        + rsi
                        + "): "
                        + (rsi.compareTo(SEVENTY) > 0
                                ? "aşırı alım bölgesinde yüksek yukarı yönlü momentum."
                                : rsi.compareTo(THIRTY) < 0
                                        ? "aşırı satım bölgesinde aşağı yönlü momentum."
                                        : rsi.compareTo(FIFTY) > 0
                                                ? "50 üzerinde pozitif momentum rejimi."
                                                : rsi.compareTo(FIFTY) < 0
                                                        ? "50 altında zayıf momentum rejimi."
                                                        : "50 seviyesinde nötr denge.");
        return readyComponent(ScoreComponentType.RSI, weight, score, rationale, metrics);
    }

    private ScoreComponentResult evaluateMacd(
            List<OhlcvBar> bars,
            Map<IndicatorType, IndicatorSeries> seriesMap,
            ScoreWeights weights) {
        BigDecimal weight = weights.weightOf(ScoreComponentType.MACD);
        IndicatorSeries macdSeries = seriesMap.get(IndicatorType.MACD);
        if (macdSeries == null
                || macdSeries.status() != IndicatorStatus.READY
                || macdSeries.points().size() < 2
                || bars.isEmpty()) {
            return insufficientComponent(
                    ScoreComponentType.MACD,
                    weight,
                    "En az iki geçerli histogram örneğine sahip hazır MACD serisi gerektirir.",
                    Map.of());
        }

        List<IndicatorPoint> pts = macdSeries.points();
        Map<String, BigDecimal> latest = pts.getLast().values();
        Map<String, BigDecimal> prev = pts.get(pts.size() - 2).values();
        BigDecimal macd = latest.get("macd");
        BigDecimal signal = latest.get("signal");
        BigDecimal histogram = latest.get("histogram");
        BigDecimal prevHistogram = prev.get("histogram");
        BigDecimal close = bars.getLast().close();

        if (macd == null
                || signal == null
                || histogram == null
                || prevHistogram == null
                || close.compareTo(BigDecimal.ZERO) <= 0) {
            return insufficientComponent(
                    ScoreComponentType.MACD,
                    weight,
                    "MACD, momentum değişimi için ısınma sonrası en az iki histogram barı gerektirir.",
                    Map.of());
        }

        BigDecimal macdRatio = macd.divide(close, MC);
        BigDecimal histRatio = histogram.divide(close, MC);
        BigDecimal deltaRatio = histogram.subtract(prevHistogram, MC).divide(close, MC);

        BigDecimal macdPart =
                clamp(macdRatio.multiply(BigDecimal.valueOf(2000L), MC), new BigDecimal("-40"), new BigDecimal("40"));
        BigDecimal histPart =
                clamp(histRatio.multiply(BigDecimal.valueOf(4000L), MC), new BigDecimal("-40"), new BigDecimal("40"));
        BigDecimal deltaPart =
                clamp(deltaRatio.multiply(BigDecimal.valueOf(8000L), MC), new BigDecimal("-20"), new BigDecimal("20"));

        BigDecimal score = scaleScore(clamp(macdPart.add(histPart, MC).add(deltaPart, MC), NEGATIVE_HUNDRED, HUNDRED));

        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        metrics.put("macd", macd);
        metrics.put("signal", signal);
        metrics.put("histogram", histogram);

        String rationale =
                "MACD ("
                        + macd
                        + ") / Sinyal ("
                        + signal
                        + "), histogram "
                        + histogram
                        + " (önceki "
                        + prevHistogram
                        + ").";
        return readyComponent(ScoreComponentType.MACD, weight, score, rationale, metrics);
    }

    private ScoreComponentResult evaluateVolume(
            List<OhlcvBar> bars,
            Map<IndicatorType, IndicatorSeries> seriesMap,
            ScoreWeights weights) {
        BigDecimal weight = weights.weightOf(ScoreComponentType.VOLUME);
        IndicatorSeries volSeries = seriesMap.get(IndicatorType.VOLUME_AVG);
        if (bars.size() < 2
                || volSeries == null
                || volSeries.status() != IndicatorStatus.READY) {
            return insufficientComponent(
                    ScoreComponentType.VOLUME,
                    weight,
                    "En az 2 bar ve hazır hareketli hacim ortalaması gerektirir.",
                    Map.of());
        }

        BigDecimal ratio = volSeries.latestValues().get("ratio");
        BigDecimal average = volSeries.latestValues().get("average");
        if (ratio == null || average == null || average.compareTo(BigDecimal.ZERO) <= 0) {
            Map<String, BigDecimal> metrics = new LinkedHashMap<>();
            metrics.put("averageVolume", average);
            metrics.put("volumeRatio", ratio);
            return insufficientComponent(
                    ScoreComponentType.VOLUME,
                    weight,
                    "Hareketli ortalama hacim sıfır veya kullanılamıyor; hacim katılımı değerlendirilemiyor.",
                    metrics);
        }

        BigDecimal prevClose = bars.get(bars.size() - 2).close();
        BigDecimal close = bars.getLast().close();
        int direction = close.compareTo(prevClose);

        BigDecimal score;
        if (direction == 0) {
            score = ZERO;
        } else {
            BigDecimal conviction = clamp(ratio.multiply(FIFTY, MC), ZERO, HUNDRED);
            score = scaleScore(direction > 0 ? conviction : conviction.negate());
        }

        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        metrics.put("averageVolume", average);
        metrics.put("volumeRatio", ratio);

        String rationale =
                "Hacim oranı "
                        + ratio
                        + "x (hareketli ortalamaya göre), son bar kapanışı "
                        + (direction > 0 ? "pozitif" : direction < 0 ? "negatif" : "yatay")
                        + ".";
        return readyComponent(ScoreComponentType.VOLUME, weight, score, rationale, metrics);
    }

    private ScoreComponentResult evaluateBollinger(
            List<OhlcvBar> bars,
            Map<IndicatorType, IndicatorSeries> seriesMap,
            ScoreWeights weights) {
        BigDecimal weight = weights.weightOf(ScoreComponentType.BOLLINGER);
        IndicatorSeries bbSeries = seriesMap.get(IndicatorType.BOLLINGER);
        if (bars.isEmpty() || bbSeries == null || bbSeries.status() != IndicatorStatus.READY) {
            return insufficientComponent(
                    ScoreComponentType.BOLLINGER,
                    weight,
                    "Hazır Bollinger Bantları serisi gerektirir.",
                    Map.of());
        }

        BigDecimal upper = bbSeries.latestValues().get("upper");
        BigDecimal middle = bbSeries.latestValues().get("middle");
        BigDecimal lower = bbSeries.latestValues().get("lower");
        BigDecimal bandwidth = bbSeries.latestValues().get("bandwidth");
        if (upper == null || middle == null || lower == null) {
            return insufficientComponent(
                    ScoreComponentType.BOLLINGER,
                    weight,
                    "Bollinger Bant değerleri kullanılamıyor.",
                    Map.of());
        }

        BigDecimal close = bars.getLast().close();
        BigDecimal span = upper.subtract(lower, MC);
        BigDecimal percentB;
        BigDecimal score;
        if (span.compareTo(BigDecimal.ZERO) <= 0) {
            percentB = new BigDecimal("0.500000");
            score = ZERO;
        } else {
            percentB = close.subtract(lower, MC).divide(span, MC);
            BigDecimal half = new BigDecimal("0.5");
            if (percentB.compareTo(BigDecimal.ONE) > 0) {
                score =
                        clamp(
                                EIGHTY.subtract(
                                        percentB.subtract(BigDecimal.ONE, MC).multiply(HUNDRED, MC),
                                        MC),
                                ZERO,
                                EIGHTY);
            } else if (percentB.compareTo(BigDecimal.ZERO) < 0) {
                score =
                        clamp(
                                NEG_EIGHTY.add(percentB.negate().multiply(HUNDRED, MC), MC),
                                NEG_EIGHTY,
                                ZERO);
            } else {
                score = percentB.subtract(half, MC).multiply(new BigDecimal("160"), MC);
            }
            score = scaleScore(clamp(score, NEGATIVE_HUNDRED, HUNDRED));
        }

        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        metrics.put("upper", upper);
        metrics.put("middle", middle);
        metrics.put("lower", lower);
        metrics.put("bandwidth", bandwidth);
        metrics.put("percentB", FinancialMath.scaleIndicator(percentB));

        String rationale =
                "Kapanış ("
                        + close
                        + "), Bollinger Bantları ["
                        + lower
                        + ", "
                        + upper
                        + "] içinde %B="
                        + FinancialMath.scaleIndicator(percentB)
                        + " konumunda.";
        return readyComponent(ScoreComponentType.BOLLINGER, weight, score, rationale, metrics);
    }

    private ScoreComponentResult evaluateMomentum(
            List<OhlcvBar> bars,
            Map<IndicatorType, IndicatorSeries> seriesMap,
            ScoreWeights weights) {
        BigDecimal weight = weights.weightOf(ScoreComponentType.MOMENTUM);
        IndicatorSeries stochSeries = seriesMap.get(IndicatorType.STOCHASTIC);
        if (bars.size() < 11
                || stochSeries == null
                || stochSeries.status() != IndicatorStatus.READY) {
            return insufficientComponent(
                    ScoreComponentType.MOMENTUM,
                    weight,
                    "En az 11 bar ve hazır Stokastik (%K/%D) serisi gerektirir.",
                    Map.of());
        }

        BigDecimal stochK = stochSeries.latestValues().get("k");
        BigDecimal stochD = stochSeries.latestValues().get("d");
        BigDecimal close = bars.getLast().close();
        BigDecimal close10Ago = bars.get(bars.size() - 11).close();
        if (stochK == null || stochD == null || close10Ago.compareTo(BigDecimal.ZERO) <= 0) {
            return insufficientComponent(
                    ScoreComponentType.MOMENTUM,
                    weight,
                    "Stokastik veya 10 barlık fiyat momentum referansı kullanılamıyor.",
                    Map.of());
        }

        BigDecimal roc10Pct =
                close.subtract(close10Ago, MC).divide(close10Ago, MC).multiply(HUNDRED, MC);
        BigDecimal rocPart =
                clamp(roc10Pct.multiply(BigDecimal.valueOf(6L), MC), new BigDecimal("-50"), new BigDecimal("50"));
        BigDecimal stochBase = stochK.subtract(FIFTY, MC).multiply(new BigDecimal("0.8"), MC);
        BigDecimal stochCross = stochK.subtract(stochD, MC).multiply(new BigDecimal("0.5"), MC);
        BigDecimal stochPart =
                clamp(stochBase.add(stochCross, MC), new BigDecimal("-50"), new BigDecimal("50"));

        BigDecimal score = scaleScore(clamp(rocPart.add(stochPart, MC), NEGATIVE_HUNDRED, HUNDRED));

        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        metrics.put("roc10Pct", FinancialMath.scalePercentage(roc10Pct));
        metrics.put("stochasticK", stochK);
        metrics.put("stochasticD", stochD);

        String rationale =
                "10 barlık ROC %"
                        + FinancialMath.scalePercentage(roc10Pct)
                        + "; Stokastik %K="
                        + stochK
                        + ", %D="
                        + stochD
                        + ".";
        return readyComponent(ScoreComponentType.MOMENTUM, weight, score, rationale, metrics);
    }

    private ScoreComponentResult evaluateSupportResistance(
            List<OhlcvBar> bars, ScoreWeights weights) {
        BigDecimal weight = weights.weightOf(ScoreComponentType.SUPPORT_RESISTANCE);
        int window = 20;
        if (bars.size() < window) {
            return insufficientComponent(
                    ScoreComponentType.SUPPORT_RESISTANCE,
                    weight,
                    "İzleyen destek ve direnç seviyeleri için en az 20 bar gerektirir.",
                    Map.of());
        }

        int start = bars.size() - window;
        BigDecimal support = bars.get(start).low();
        BigDecimal resistance = bars.get(start).high();
        for (int i = start + 1; i < bars.size(); i++) {
            support = support.min(bars.get(i).low());
            resistance = resistance.max(bars.get(i).high());
        }

        BigDecimal close = bars.getLast().close();
        BigDecimal range = resistance.subtract(support, MC);
        BigDecimal position;
        BigDecimal score;
        if (range.compareTo(BigDecimal.ZERO) <= 0) {
            position = new BigDecimal("0.500000");
            score = ZERO;
        } else {
            position = close.subtract(support, MC).divide(range, MC);
            score =
                    scaleScore(
                            clamp(
                                    position.subtract(new BigDecimal("0.5"), MC)
                                            .multiply(new BigDecimal("180"), MC),
                                    NEGATIVE_HUNDRED,
                                    HUNDRED));
        }

        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        metrics.put("support20", support);
        metrics.put("resistance20", resistance);
        metrics.put("rangePosition", FinancialMath.scaleIndicator(position));

        String rationale =
                "Kapanış ("
                        + close
                        + "), 20 barlık aralık ["
                        + support
                        + " .. "
                        + resistance
                        + "] içinde %"
                        + FinancialMath.scalePercentage(position.multiply(HUNDRED, MC))
                        + " konumunda.";
        return readyComponent(
                ScoreComponentType.SUPPORT_RESISTANCE, weight, score, rationale, metrics);
    }

    private static ScoreComponentResult readyComponent(
            ScoreComponentType type,
            BigDecimal weightPercent,
            BigDecimal normalizedScore,
            String explanation,
            Map<String, BigDecimal> metrics) {
        BigDecimal contribution =
                FinancialMath.scaleMoney(
                        normalizedScore
                                .multiply(weightPercent, MC)
                                .divide(TWO_HUNDRED, MC));
        return new ScoreComponentResult(
                type,
                IndicatorStatus.READY,
                weightPercent,
                normalizedScore,
                contribution,
                explanation,
                metrics);
    }

    private static ScoreComponentResult insufficientComponent(
            ScoreComponentType type,
            BigDecimal weightPercent,
            String explanation,
            Map<String, BigDecimal> metrics) {
        return new ScoreComponentResult(
                type,
                IndicatorStatus.INSUFFICIENT_DATA,
                weightPercent,
                null,
                null,
                explanation,
                metrics);
    }

    private static TrendDirection deriveTrendDirection(ScoreComponentResult trendComponent) {
        if (trendComponent.status() != IndicatorStatus.READY
                || trendComponent.normalizedScore() == null) {
            return TrendDirection.UNKNOWN;
        }
        BigDecimal score = trendComponent.normalizedScore();
        if (score.compareTo(BULLISH_TREND_THRESHOLD) >= 0) {
            return TrendDirection.BULLISH;
        }
        if (score.compareTo(BEARISH_TREND_THRESHOLD) <= 0) {
            return TrendDirection.BEARISH;
        }
        return TrendDirection.NEUTRAL;
    }

    private static boolean isDataStale(
            List<OhlcvBar> orderedBars, Instant asOf, long maxDataAgeHours) {
        if (orderedBars.isEmpty()) {
            return true;
        }
        Instant latestBarTimestamp = orderedBars.getLast().timestamp();
        return Duration.between(latestBarTimestamp, asOf)
                        .compareTo(Duration.ofHours(maxDataAgeHours))
                > 0;
    }

    private static BigDecimal extractLatestRsi(Map<IndicatorType, IndicatorSeries> seriesMap) {
        IndicatorSeries rsi = seriesMap.get(IndicatorType.RSI);
        if (rsi == null || rsi.status() != IndicatorStatus.READY) {
            return null;
        }
        return rsi.latestValues().get("value");
    }

    private static ScoreExplanation buildReadyExplanation(
            String symbol,
            BigDecimal finalScore,
            ScoreRegime regime,
            TrendDirection trendDirection,
            List<ScoreComponentResult> components,
            boolean staleData,
            boolean simulated) {
        String headline =
                symbol
                        + " bileşik teknik skoru "
                        + finalScore
                        + "/100 ("
                        + localizeRegime(regime)
                        + ", trend: "
                        + localizeTrend(trendDirection)
                        + ").";

        List<String> keyDrivers =
                components.stream()
                        .sorted(
                                Comparator.comparing(
                                                (ScoreComponentResult c) ->
                                                        c.weightedContribution().abs())
                                        .reversed())
                        .limit(4)
                        .map(
                                c ->
                                        localizeComponent(c.type())
                                                + " (puan "
                                                + c.normalizedScore()
                                                + ", ağırlık %"
                                                + c.weightPercent()
                                                + "): "
                                                + c.explanation())
                        .toList();

        List<String> notes = new ArrayList<>();
        if (simulated) {
            notes.add(
                    "Simüle deterministik mock verilerden hesaplanmıştır; canlı piyasa fiyatları değildir.");
        }
        if (staleData) {
            notes.add("Son giriş barı tercih edilen tazelik eşiğini aşmıştır.");
        }
        notes.add("Teknik göstergeler yalnızca geçmiş fiyat/hacmi özetler; gelecekteki getiriyi öngörmez.");

        String methodologySummary =
                ALGORITHM_VERSION
                        + " altında [-100, +100] aralığına normalize edilmiş 7 bileşenin ağırlıklı ortalaması doğrusal olarak [0, 100] ölçeğine eşlenir.";

        return new ScoreExplanation(headline, keyDrivers, notes, methodologySummary, DISCLAIMER);
    }

    private static ScoreExplanation buildInsufficientExplanation(
            String symbol,
            List<String> reasons,
            boolean staleData,
            boolean simulated) {
        String headline =
                symbol + " teknik skoru INSUFFICIENT_DATA olarak durduruldu ve hesaplanmadı.";

        List<String> notes = new ArrayList<>(reasons);
        if (simulated) {
            notes.add("Etkin sağlayıcı yalnızca simüle mock veri üretmektedir.");
        }
        if (staleData) {
            notes.add("Piyasa verisi tazelik koşulu sağlanamadı.");
        }

        String methodologySummary =
                "Skorlama, tüm 7 bileşende yeterli uzunlukta, geçerli ve bayat olmayan veri geçmişi gerektirir ("
                        + ALGORITHM_VERSION
                        + ").";

        return new ScoreExplanation(
                headline, Collections.emptyList(), notes, methodologySummary, DISCLAIMER);
    }

    private static String localizeRegime(ScoreRegime regime) {
        return switch (regime) {
            case STRONG_BULLISH_TECHNICAL -> "Güçlü Pozitif Teknik";
            case MODERATE_BULLISH_TECHNICAL -> "Ilımlı Pozitif Teknik";
            case NEUTRAL_TECHNICAL -> "Nötr Teknik";
            case MODERATE_BEARISH_TECHNICAL -> "Ilımlı Negatif Teknik";
            case STRONG_BEARISH_TECHNICAL -> "Güçlü Negatif Teknik";
            case INSUFFICIENT_DATA -> "Yetersiz Veri";
        };
    }

    private static String localizeTrend(TrendDirection trendDirection) {
        return switch (trendDirection) {
            case BULLISH -> "Yükseliş";
            case BEARISH -> "Düşüş";
            case NEUTRAL -> "Nötr";
            case UNKNOWN -> "Belirsiz";
        };
    }

    private static String localizeComponent(ScoreComponentType type) {
        return switch (type) {
            case TREND -> "Trend";
            case RSI -> "RSI";
            case MACD -> "MACD";
            case VOLUME -> "Hacim";
            case BOLLINGER -> "Bollinger Bantları";
            case MOMENTUM -> "Momentum";
            case SUPPORT_RESISTANCE -> "Destek / Direnç";
        };
    }

    private static BigDecimal scaleScore(BigDecimal value) {
        return FinancialMath.scaleMoney(value);
    }

    private static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value.compareTo(min) < 0) {
            return min;
        }
        if (value.compareTo(max) > 0) {
            return max;
        }
        return value;
    }
}
