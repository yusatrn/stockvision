package com.stockvision.technicalanalysis.domain;

import com.stockvision.common.domain.FinancialMath;
import com.stockvision.marketdata.domain.OhlcvBar;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure {@link BigDecimal} technical indicator engine enforcing strict bar validation, explicit
 * warm-up nulls, zero look-ahead bias, and {@link FinancialMath#ROUNDING_MODE} rounding.
 *
 * <p>Implemented directly behind {@link TechnicalIndicatorService} instead of TA4J so that all
 * calculations obey {@link FinancialMath} precision, allow explicit {@code null} outputs during
 * warm-up or zero-volume bars, and remain free of third-party domain coupling.
 */
public class DeterministicTechnicalIndicatorEngine implements TechnicalIndicatorService {

    public static final String ENGINE_VERSION = "ta-engine-v1.0.0";

    private static final MathContext MC = FinancialMath.MATH_CONTEXT;
    private static final BigDecimal TWO = BigDecimal.valueOf(2L);
    private static final BigDecimal FIFTY = BigDecimal.valueOf(50L);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100L);
    private static final BigDecimal ZERO_SCALED = FinancialMath.scaleIndicator(BigDecimal.ZERO);
    private static final BigDecimal FIFTY_SCALED = FinancialMath.scaleIndicator(FIFTY);
    private static final BigDecimal HUNDRED_SCALED = FinancialMath.scaleIndicator(HUNDRED);

    @Override
    public String engineVersion() {
        return ENGINE_VERSION;
    }

    @Override
    public TechnicalAnalysisResult calculate(
            List<OhlcvBar> orderedBars,
            List<IndicatorType> requestedIndicators,
            IndicatorConfiguration configuration) {
        BarSeriesValidator.validate(orderedBars);
        Objects.requireNonNull(requestedIndicators, "requestedIndicators must not be null");
        Objects.requireNonNull(configuration, "configuration must not be null");
        if (requestedIndicators.isEmpty()) {
            throw new IllegalArgumentException("requestedIndicators must not be empty");
        }

        Set<IndicatorType> uniqueIndicators = new LinkedHashSet<>(requestedIndicators);
        List<IndicatorSeries> seriesList = new ArrayList<>(uniqueIndicators.size());
        int readyCount = 0;

        for (IndicatorType type : uniqueIndicators) {
            IndicatorSeries series = calculateSingle(type, orderedBars, configuration);
            if (series.status() == IndicatorStatus.READY) {
                readyCount++;
            }
            seriesList.add(series);
        }

        AnalysisRunStatus overallStatus;
        if (readyCount == 0) {
            overallStatus = AnalysisRunStatus.INSUFFICIENT_DATA;
        } else if (readyCount == seriesList.size()) {
            overallStatus = AnalysisRunStatus.READY;
        } else {
            overallStatus = AnalysisRunStatus.PARTIAL;
        }

        return new TechnicalAnalysisResult(
                ENGINE_VERSION, overallStatus, orderedBars.size(), seriesList);
    }

    private IndicatorSeries calculateSingle(
            IndicatorType type, List<OhlcvBar> bars, IndicatorConfiguration config) {
        return switch (type) {
            case SMA -> calculateSma(bars, config);
            case EMA -> calculateEma(bars, config);
            case RSI -> calculateRsi(bars, config);
            case MACD -> calculateMacd(bars, config);
            case BOLLINGER -> calculateBollinger(bars, config);
            case STOCHASTIC -> calculateStochastic(bars, config);
            case ATR -> calculateAtr(bars, config);
            case VOLUME_AVG -> calculateVolumeAverage(bars, config);
        };
    }

    private IndicatorSeries calculateSma(List<OhlcvBar> bars, IndicatorConfiguration config) {
        int period = config.smaPeriod();
        int requiredBars = period;
        int n = bars.size();
        List<IndicatorPoint> points = new ArrayList<>(n);

        BigDecimal runningSum = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            runningSum = runningSum.add(bars.get(i).close(), MC);
            if (i >= period) {
                runningSum = runningSum.subtract(bars.get(i - period).close(), MC);
            }

            boolean warmUp = i < period - 1;
            BigDecimal value =
                    warmUp
                            ? null
                            : FinancialMath.scaleIndicator(
                                    runningSum.divide(BigDecimal.valueOf(period), MC));
            points.add(new IndicatorPoint(bars.get(i).timestamp(), warmUp, singleValueMap("value", value)));
        }

        return buildSeries(
                IndicatorType.SMA,
                requiredBars,
                n,
                config.forIndicator(IndicatorType.SMA),
                points);
    }

    private IndicatorSeries calculateEma(List<OhlcvBar> bars, IndicatorConfiguration config) {
        int period = config.emaPeriod();
        int requiredBars = period;
        List<BigDecimal> closes = extractCloses(bars);
        BigDecimal[] rawEma = computeEmaArray(closes, period);

        int n = bars.size();
        List<IndicatorPoint> points = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            boolean warmUp = rawEma[i] == null;
            BigDecimal value = warmUp ? null : FinancialMath.scaleIndicator(rawEma[i]);
            points.add(
                    new IndicatorPoint(
                            bars.get(i).timestamp(), warmUp, singleValueMap("value", value)));
        }

        return buildSeries(
                IndicatorType.EMA,
                requiredBars,
                n,
                config.forIndicator(IndicatorType.EMA),
                points);
    }

    private IndicatorSeries calculateRsi(List<OhlcvBar> bars, IndicatorConfiguration config) {
        int period = config.rsiPeriod();
        int requiredBars = period + 1;
        int n = bars.size();
        List<IndicatorPoint> points = new ArrayList<>(n);

        if (n == 0) {
            return buildSeries(
                    IndicatorType.RSI,
                    requiredBars,
                    0,
                    config.forIndicator(IndicatorType.RSI),
                    points);
        }

        BigDecimal periodBd = BigDecimal.valueOf(period);
        BigDecimal periodMinusOneBd = BigDecimal.valueOf(period - 1L);
        BigDecimal gainSum = BigDecimal.ZERO;
        BigDecimal lossSum = BigDecimal.ZERO;
        BigDecimal avgGain = null;
        BigDecimal avgLoss = null;

        points.add(new IndicatorPoint(bars.get(0).timestamp(), true, singleValueMap("value", null)));

        for (int i = 1; i < n; i++) {
            BigDecimal delta = bars.get(i).close().subtract(bars.get(i - 1).close(), MC);
            BigDecimal gain = delta.compareTo(BigDecimal.ZERO) > 0 ? delta : BigDecimal.ZERO;
            BigDecimal loss = delta.compareTo(BigDecimal.ZERO) < 0 ? delta.negate() : BigDecimal.ZERO;

            if (i < period) {
                gainSum = gainSum.add(gain, MC);
                lossSum = lossSum.add(loss, MC);
                points.add(
                        new IndicatorPoint(
                                bars.get(i).timestamp(), true, singleValueMap("value", null)));
            } else if (i == period) {
                gainSum = gainSum.add(gain, MC);
                lossSum = lossSum.add(loss, MC);
                avgGain = gainSum.divide(periodBd, MC);
                avgLoss = lossSum.divide(periodBd, MC);
                points.add(
                        new IndicatorPoint(
                                bars.get(i).timestamp(),
                                false,
                                singleValueMap("value", computeRsiValue(avgGain, avgLoss))));
            } else {
                avgGain = avgGain.multiply(periodMinusOneBd, MC).add(gain, MC).divide(periodBd, MC);
                avgLoss = avgLoss.multiply(periodMinusOneBd, MC).add(loss, MC).divide(periodBd, MC);
                points.add(
                        new IndicatorPoint(
                                bars.get(i).timestamp(),
                                false,
                                singleValueMap("value", computeRsiValue(avgGain, avgLoss))));
            }
        }

        return buildSeries(
                IndicatorType.RSI,
                requiredBars,
                n,
                config.forIndicator(IndicatorType.RSI),
                points);
    }

    private IndicatorSeries calculateMacd(List<OhlcvBar> bars, IndicatorConfiguration config) {
        int fastPeriod = config.macdFastPeriod();
        int slowPeriod = config.macdSlowPeriod();
        int signalPeriod = config.macdSignalPeriod();
        int requiredBars = slowPeriod + signalPeriod - 1;
        int n = bars.size();
        List<IndicatorPoint> points = new ArrayList<>(n);

        if (n == 0) {
            return buildSeries(
                    IndicatorType.MACD,
                    requiredBars,
                    0,
                    config.forIndicator(IndicatorType.MACD),
                    points);
        }

        List<BigDecimal> closes = extractCloses(bars);
        BigDecimal[] fastEma = computeEmaArray(closes, fastPeriod);
        BigDecimal[] slowEma = computeEmaArray(closes, slowPeriod);

        BigDecimal[] macdLine = new BigDecimal[n];
        for (int i = slowPeriod - 1; i < n; i++) {
            macdLine[i] = fastEma[i].subtract(slowEma[i], MC);
        }

        int signalSeedIndex = slowPeriod + signalPeriod - 2;
        BigDecimal[] signalLine = new BigDecimal[n];
        if (n > signalSeedIndex) {
            BigDecimal sum = BigDecimal.ZERO;
            for (int i = slowPeriod - 1; i <= signalSeedIndex; i++) {
                sum = sum.add(macdLine[i], MC);
            }
            BigDecimal currentSignal = sum.divide(BigDecimal.valueOf(signalPeriod), MC);
            signalLine[signalSeedIndex] = currentSignal;

            BigDecimal multiplier =
                    TWO.divide(BigDecimal.valueOf(signalPeriod + 1L), MC);
            for (int i = signalSeedIndex + 1; i < n; i++) {
                currentSignal =
                        macdLine[i]
                                .subtract(currentSignal, MC)
                                .multiply(multiplier, MC)
                                .add(currentSignal, MC);
                signalLine[i] = currentSignal;
            }
        }

        for (int i = 0; i < n; i++) {
            boolean warmUp = i < signalSeedIndex;
            BigDecimal macdOut =
                    macdLine[i] != null ? FinancialMath.scaleIndicator(macdLine[i]) : null;
            BigDecimal signalOut =
                    signalLine[i] != null ? FinancialMath.scaleIndicator(signalLine[i]) : null;
            BigDecimal histogramOut =
                    (macdLine[i] != null && signalLine[i] != null)
                            ? FinancialMath.scaleIndicator(
                                    macdLine[i].subtract(signalLine[i], MC))
                            : null;

            Map<String, BigDecimal> values = new LinkedHashMap<>();
            values.put("macd", macdOut);
            values.put("signal", signalOut);
            values.put("histogram", histogramOut);
            points.add(new IndicatorPoint(bars.get(i).timestamp(), warmUp, values));
        }

        return buildSeries(
                IndicatorType.MACD,
                requiredBars,
                n,
                config.forIndicator(IndicatorType.MACD),
                points);
    }

    private IndicatorSeries calculateBollinger(List<OhlcvBar> bars, IndicatorConfiguration config) {
        int period = config.bollingerPeriod();
        BigDecimal multiplier = config.bollingerStdDevMultiplier();
        int requiredBars = period;
        int n = bars.size();
        List<IndicatorPoint> points = new ArrayList<>(n);
        BigDecimal periodBd = BigDecimal.valueOf(period);

        for (int i = 0; i < n; i++) {
            boolean warmUp = i < period - 1;
            Map<String, BigDecimal> values = new LinkedHashMap<>();
            if (warmUp) {
                values.put("upper", null);
                values.put("middle", null);
                values.put("lower", null);
                values.put("bandwidth", null);
            } else {
                BigDecimal sum = BigDecimal.ZERO;
                for (int j = i - period + 1; j <= i; j++) {
                    sum = sum.add(bars.get(j).close(), MC);
                }
                BigDecimal middle = sum.divide(periodBd, MC);

                BigDecimal squaredDiffSum = BigDecimal.ZERO;
                for (int j = i - period + 1; j <= i; j++) {
                    BigDecimal diff = bars.get(j).close().subtract(middle, MC);
                    squaredDiffSum = squaredDiffSum.add(diff.multiply(diff, MC), MC);
                }
                BigDecimal variance = squaredDiffSum.divide(periodBd, MC);
                BigDecimal stdDev =
                        variance.compareTo(BigDecimal.ZERO) <= 0
                                ? BigDecimal.ZERO
                                : variance.sqrt(MC);

                BigDecimal bandOffset = multiplier.multiply(stdDev, MC);
                BigDecimal upper = middle.add(bandOffset, MC);
                BigDecimal lower = middle.subtract(bandOffset, MC);
                BigDecimal bandwidth =
                        middle.compareTo(BigDecimal.ZERO) > 0
                                ? upper.subtract(lower, MC).divide(middle, MC)
                                : null;

                values.put("upper", FinancialMath.scaleIndicator(upper));
                values.put("middle", FinancialMath.scaleIndicator(middle));
                values.put("lower", FinancialMath.scaleIndicator(lower));
                values.put(
                        "bandwidth",
                        bandwidth != null ? FinancialMath.scaleIndicator(bandwidth) : null);
            }
            points.add(new IndicatorPoint(bars.get(i).timestamp(), warmUp, values));
        }

        return buildSeries(
                IndicatorType.BOLLINGER,
                requiredBars,
                n,
                config.forIndicator(IndicatorType.BOLLINGER),
                points);
    }

    private IndicatorSeries calculateStochastic(
            List<OhlcvBar> bars, IndicatorConfiguration config) {
        int kPeriod = config.stochasticKPeriod();
        int dPeriod = config.stochasticDPeriod();
        int requiredBars = kPeriod + dPeriod - 1;
        int n = bars.size();
        List<IndicatorPoint> points = new ArrayList<>(n);

        BigDecimal[] rawK = new BigDecimal[n];
        for (int i = kPeriod - 1; i < n; i++) {
            BigDecimal highestHigh = bars.get(i - kPeriod + 1).high();
            BigDecimal lowestLow = bars.get(i - kPeriod + 1).low();
            for (int j = i - kPeriod + 2; j <= i; j++) {
                highestHigh = highestHigh.max(bars.get(j).high());
                lowestLow = lowestLow.min(bars.get(j).low());
            }
            BigDecimal range = highestHigh.subtract(lowestLow, MC);
            if (range.compareTo(BigDecimal.ZERO) == 0) {
                rawK[i] = FIFTY;
            } else {
                rawK[i] =
                        bars.get(i)
                                .close()
                                .subtract(lowestLow, MC)
                                .multiply(HUNDRED, MC)
                                .divide(range, MC);
            }
        }

        int dStartIndex = kPeriod + dPeriod - 2;
        BigDecimal dPeriodBd = BigDecimal.valueOf(dPeriod);
        for (int i = 0; i < n; i++) {
            boolean warmUp = i < dStartIndex;
            BigDecimal kOut = rawK[i] != null ? FinancialMath.scaleIndicator(rawK[i]) : null;
            BigDecimal dOut = null;
            if (!warmUp) {
                BigDecimal sumK = BigDecimal.ZERO;
                for (int j = i - dPeriod + 1; j <= i; j++) {
                    sumK = sumK.add(rawK[j], MC);
                }
                dOut = FinancialMath.scaleIndicator(sumK.divide(dPeriodBd, MC));
            }
            Map<String, BigDecimal> values = new LinkedHashMap<>();
            values.put("k", kOut);
            values.put("d", dOut);
            points.add(new IndicatorPoint(bars.get(i).timestamp(), warmUp, values));
        }

        return buildSeries(
                IndicatorType.STOCHASTIC,
                requiredBars,
                n,
                config.forIndicator(IndicatorType.STOCHASTIC),
                points);
    }

    private IndicatorSeries calculateAtr(List<OhlcvBar> bars, IndicatorConfiguration config) {
        int period = config.atrPeriod();
        int requiredBars = period + 1;
        int n = bars.size();
        List<IndicatorPoint> points = new ArrayList<>(n);

        if (n == 0) {
            return buildSeries(
                    IndicatorType.ATR,
                    requiredBars,
                    0,
                    config.forIndicator(IndicatorType.ATR),
                    points);
        }

        points.add(new IndicatorPoint(bars.get(0).timestamp(), true, singleValueMap("value", null)));

        BigDecimal periodBd = BigDecimal.valueOf(period);
        BigDecimal periodMinusOneBd = BigDecimal.valueOf(period - 1L);
        BigDecimal trSum = BigDecimal.ZERO;
        BigDecimal currentAtr = null;

        for (int i = 1; i < n; i++) {
            OhlcvBar current = bars.get(i);
            BigDecimal prevClose = bars.get(i - 1).close();
            BigDecimal highMinusLow = current.high().subtract(current.low(), MC);
            BigDecimal highMinusPrevClose = current.high().subtract(prevClose, MC).abs();
            BigDecimal lowMinusPrevClose = current.low().subtract(prevClose, MC).abs();
            BigDecimal trueRange = highMinusLow.max(highMinusPrevClose).max(lowMinusPrevClose);

            if (i < period) {
                trSum = trSum.add(trueRange, MC);
                points.add(
                        new IndicatorPoint(
                                current.timestamp(), true, singleValueMap("value", null)));
            } else if (i == period) {
                trSum = trSum.add(trueRange, MC);
                currentAtr = trSum.divide(periodBd, MC);
                points.add(
                        new IndicatorPoint(
                                current.timestamp(),
                                false,
                                singleValueMap(
                                        "value", FinancialMath.scaleIndicator(currentAtr))));
            } else {
                currentAtr =
                        currentAtr
                                .multiply(periodMinusOneBd, MC)
                                .add(trueRange, MC)
                                .divide(periodBd, MC);
                points.add(
                        new IndicatorPoint(
                                current.timestamp(),
                                false,
                                singleValueMap(
                                        "value", FinancialMath.scaleIndicator(currentAtr))));
            }
        }

        return buildSeries(
                IndicatorType.ATR,
                requiredBars,
                n,
                config.forIndicator(IndicatorType.ATR),
                points);
    }

    private IndicatorSeries calculateVolumeAverage(
            List<OhlcvBar> bars, IndicatorConfiguration config) {
        int period = config.volumeAvgPeriod();
        int requiredBars = period;
        int n = bars.size();
        List<IndicatorPoint> points = new ArrayList<>(n);
        BigDecimal periodBd = BigDecimal.valueOf(period);

        BigDecimal runningSum = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            BigDecimal currentVolume = BigDecimal.valueOf(bars.get(i).volume());
            runningSum = runningSum.add(currentVolume, MC);
            if (i >= period) {
                runningSum = runningSum.subtract(BigDecimal.valueOf(bars.get(i - period).volume()), MC);
            }

            boolean warmUp = i < period - 1;
            Map<String, BigDecimal> values = new LinkedHashMap<>();
            if (warmUp) {
                values.put("average", null);
                values.put("ratio", null);
            } else {
                BigDecimal avg = runningSum.divide(periodBd, MC);
                BigDecimal ratio =
                        avg.compareTo(BigDecimal.ZERO) > 0
                                ? currentVolume.divide(avg, MC)
                                : null;
                values.put("average", FinancialMath.scaleIndicator(avg));
                values.put("ratio", ratio != null ? FinancialMath.scaleIndicator(ratio) : null);
            }
            points.add(new IndicatorPoint(bars.get(i).timestamp(), warmUp, values));
        }

        return buildSeries(
                IndicatorType.VOLUME_AVG,
                requiredBars,
                n,
                config.forIndicator(IndicatorType.VOLUME_AVG),
                points);
    }

    private static BigDecimal[] computeEmaArray(List<BigDecimal> values, int period) {
        int n = values.size();
        BigDecimal[] result = new BigDecimal[n];
        if (n < period) {
            return result;
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(values.get(i), MC);
        }
        BigDecimal ema = sum.divide(BigDecimal.valueOf(period), MC);
        result[period - 1] = ema;

        BigDecimal multiplier = TWO.divide(BigDecimal.valueOf(period + 1L), MC);
        for (int i = period; i < n; i++) {
            ema = values.get(i).subtract(ema, MC).multiply(multiplier, MC).add(ema, MC);
            result[i] = ema;
        }
        return result;
    }

    private static BigDecimal computeRsiValue(BigDecimal avgGain, BigDecimal avgLoss) {
        boolean zeroGain = avgGain.compareTo(BigDecimal.ZERO) == 0;
        boolean zeroLoss = avgLoss.compareTo(BigDecimal.ZERO) == 0;
        if (zeroGain && zeroLoss) {
            return FIFTY_SCALED;
        }
        if (zeroLoss) {
            return HUNDRED_SCALED;
        }
        if (zeroGain) {
            return ZERO_SCALED;
        }
        BigDecimal rs = avgGain.divide(avgLoss, MC);
        BigDecimal rsi = HUNDRED.subtract(HUNDRED.divide(BigDecimal.ONE.add(rs, MC), MC), MC);
        return FinancialMath.scaleIndicator(rsi);
    }

    private static List<BigDecimal> extractCloses(List<OhlcvBar> bars) {
        List<BigDecimal> closes = new ArrayList<>(bars.size());
        for (OhlcvBar bar : bars) {
            closes.add(bar.close());
        }
        return closes;
    }

    private static Map<String, BigDecimal> singleValueMap(String key, BigDecimal value) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        map.put(key, value);
        return Collections.unmodifiableMap(map);
    }

    private static IndicatorSeries buildSeries(
            IndicatorType type,
            int requiredBars,
            int availableBars,
            Map<String, Object> parameters,
            List<IndicatorPoint> points) {
        boolean ready = availableBars >= requiredBars && !points.isEmpty();
        IndicatorStatus status = ready ? IndicatorStatus.READY : IndicatorStatus.INSUFFICIENT_DATA;
        int warmUpBars = Math.min(availableBars, Math.max(0, requiredBars - 1));
        Map<String, BigDecimal> latestValues = ready ? points.getLast().values() : Map.of();

        return new IndicatorSeries(
                type,
                status,
                warmUpBars,
                requiredBars,
                availableBars,
                parameters,
                latestValues,
                points);
    }
}
