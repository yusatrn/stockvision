package com.stockvision.scoring.domain;

import com.stockvision.common.domain.FinancialMath;
import com.stockvision.marketdata.domain.LatestQuote;
import com.stockvision.marketdata.domain.OhlcvBar;
import com.stockvision.technicalanalysis.domain.IndicatorSeries;
import com.stockvision.technicalanalysis.domain.IndicatorStatus;
import com.stockvision.technicalanalysis.domain.IndicatorType;
import com.stockvision.technicalanalysis.domain.TechnicalAnalysisResult;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure {@link BigDecimal} deterministic setup analyzer producing momentum/breakout scores,
 * mathematical support/watch/resistance/invalidation reference levels, risk/reward metrics, and
 * BIST ±10% theoretical margin references.
 *
 * <p>Never issues buy/sell advice or ceiling predictions, and rejects missing, zero-ATR, or stale
 * inputs with {@link ScoreStatus#INSUFFICIENT_DATA}.
 */
public class DeterministicTechnicalSetupEngine implements TechnicalSetupService {

    public static final String ENGINE_VERSION = "setup-engine-v1.0.0";

    private static final MathContext MC = FinancialMath.MATH_CONTEXT;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal BIST_CEILING_FACTOR = new BigDecimal("1.10");
    private static final BigDecimal BIST_FLOOR_FACTOR = new BigDecimal("0.90");
    private static final BigDecimal ZERO_POINT_ZERO_ONE = new BigDecimal("0.01");
    private static final BigDecimal MIN_PRICE_UNIT = new BigDecimal("0.0001");

    private static final List<String> STANDARD_INDICATORS =
            List.of(
                    "SMA(20)",
                    "EMA(20)",
                    "RSI(14)",
                    "MACD(12,26,9)",
                    "BOLLINGER(20,2)",
                    "STOCHASTIC(14,3)",
                    "ATR(14)",
                    "VOLUME_AVG(20)",
                    "SUPPORT_RESISTANCE(20)");

    private static final String BIST_MARGIN_NOTE =
            "BIST ±%10 günlük teorik fiyat marjı referansıdır; tavan veya taban tahmini değildir.";

    private static final String DISCLAIMER =
            "Bu analiz tavan tahmini veya al/sat yatırım tavsiyesi değildir; yalnızca geçmiş ve "
                    + "gecikmeli fiyat/hacim verileri üzerinden ATR, destek/direnç ve momentum "
                    + "indikatörleriyle türetilen kural tabanlı karar destek referansları sunar. "
                    + "Kazanç veya hedef gerçekleşme iddiası içermez.";

    @Override
    public String engineVersion() {
        return ENGINE_VERSION;
    }

    @Override
    public TechnicalSetupResult analyze(
            String symbol,
            List<OhlcvBar> orderedBars,
            TechnicalAnalysisResult indicatorResult,
            TechnicalScoreResult scoreResult,
            LatestQuote latestQuote,
            boolean simulated) {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(orderedBars, "orderedBars must not be null");
        Objects.requireNonNull(indicatorResult, "indicatorResult must not be null");
        Objects.requireNonNull(scoreResult, "scoreResult must not be null");

        Map<IndicatorType, IndicatorSeries> seriesMap = new EnumMap<>(IndicatorType.class);
        for (IndicatorSeries series : indicatorResult.series()) {
            seriesMap.put(series.type(), series);
        }

        BigDecimal latestClose =
                orderedBars.isEmpty()
                        ? null
                        : FinancialMath.scalePrice(orderedBars.getLast().close());
        BigDecimal latestRsi = extractLatestValue(seriesMap.get(IndicatorType.RSI), "value");
        BigDecimal volumeRatio = extractLatestValue(seriesMap.get(IndicatorType.VOLUME_AVG), "ratio");
        BigDecimal atr14 = extractLatestValue(seriesMap.get(IndicatorType.ATR), "value");
        BigDecimal atrPercent =
                (atr14 != null && latestClose != null && latestClose.compareTo(ZERO) > 0)
                        ? FinancialMath.scalePercentage(
                                atr14.divide(latestClose, MC).multiply(HUNDRED, MC))
                        : null;

        BistMarginReference bistMargin = computeBistMargin(orderedBars, latestQuote);

        List<String> insufficientReasons =
                collectInsufficientReasons(orderedBars, seriesMap, scoreResult);
        if (!insufficientReasons.isEmpty()) {
            List<String> warnings = new ArrayList<>();
            warnings.add("Veri yeterliliği sağlanmadığı için destek, direnç ve risk sınırı üretilmedi.");
            if (simulated) {
                warnings.add("Etkin sağlayıcı simüle veri üretmektedir; gerçek piyasa verisi değildir.");
            }
            if (scoreResult.staleData()) {
                warnings.add("Fiyat verisi güncel tazelik eşiğini aşmıştır.");
            }
            return new TechnicalSetupResult(
                    ENGINE_VERSION,
                    ScoreStatus.INSUFFICIENT_DATA,
                    SetupCategory.INSUFFICIENT_DATA,
                    scoreResult.staleData(),
                    null,
                    null,
                    ScoreRegime.INSUFFICIENT_DATA,
                    scoreResult.trendDirection(),
                    latestClose,
                    latestRsi != null ? FinancialMath.scaleMoney(latestRsi) : null,
                    volumeRatio != null ? FinancialMath.scaleMoney(volumeRatio) : null,
                    atr14 != null ? FinancialMath.scalePrice(atr14) : null,
                    atrPercent,
                    null,
                    null,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    bistMargin,
                    STANDARD_INDICATORS,
                    List.of(),
                    warnings,
                    insufficientReasons,
                    symbol + " için teknik seviye analizi INSUFFICIENT_DATA olarak durduruldu.",
                    DISCLAIMER);
        }

        Map<ScoreComponentType, ScoreComponentResult> componentMap =
                new EnumMap<>(ScoreComponentType.class);
        for (ScoreComponentResult c : scoreResult.components()) {
            componentMap.put(c.type(), c);
        }

        BigDecimal close = orderedBars.getLast().close();
        BigDecimal prevClose = orderedBars.get(orderedBars.size() - 2).close();
        BigDecimal sma20 = componentMap.get(ScoreComponentType.TREND).metrics().get("sma");
        BigDecimal ema20 = componentMap.get(ScoreComponentType.TREND).metrics().get("ema");
        BigDecimal rsi = componentMap.get(ScoreComponentType.RSI).metrics().get("rsi");
        BigDecimal roc10Pct = componentMap.get(ScoreComponentType.MOMENTUM).metrics().get("roc10Pct");
        BigDecimal bbUpper = componentMap.get(ScoreComponentType.BOLLINGER).metrics().get("upper");
        BigDecimal bbMiddle = componentMap.get(ScoreComponentType.BOLLINGER).metrics().get("middle");
        BigDecimal bbLower = componentMap.get(ScoreComponentType.BOLLINGER).metrics().get("lower");
        BigDecimal percentB = componentMap.get(ScoreComponentType.BOLLINGER).metrics().get("percentB");
        BigDecimal support20 =
                componentMap.get(ScoreComponentType.SUPPORT_RESISTANCE).metrics().get("support20");
        BigDecimal resistance20 =
                componentMap.get(ScoreComponentType.SUPPORT_RESISTANCE).metrics().get("resistance20");
        BigDecimal rangePosition =
                componentMap.get(ScoreComponentType.SUPPORT_RESISTANCE).metrics().get("rangePosition");

        IndicatorSeries macdSeries = seriesMap.get(IndicatorType.MACD);
        BigDecimal macdHist = macdSeries.points().getLast().values().get("histogram");
        BigDecimal prevMacdHist =
                macdSeries.points().get(macdSeries.points().size() - 2).values().get("histogram");

        BigDecimal priorResistance19 = computePriorResistance19(orderedBars);
        BigDecimal scaledBreakoutTrigger = FinancialMath.scalePrice(priorResistance19);
        boolean breakoutConfirmed = close.compareTo(priorResistance19) > 0;
        BigDecimal distToBreakoutPct =
                FinancialMath.scalePercentage(
                        priorResistance19.subtract(close, MC).divide(close, MC).multiply(HUNDRED, MC));

        PriceZone supportWatchZone =
                buildSupportWatchZone(close, atr14, support20, sma20, ema20, bbLower, bbMiddle);
        PriceZone resistanceExitZone =
                buildResistanceExitZone(close, atr14, resistance20, bbUpper);
        BigDecimal invalidationRiskLevel =
                buildInvalidationRiskLevel(close, atr14, supportWatchZone.low());
        RiskRewardMetrics riskReward =
                computeRiskReward(
                        close,
                        invalidationRiskLevel,
                        resistanceExitZone.low(),
                        resistanceExitZone.high());

        BigDecimal momentumBreakoutScore =
                computeMomentumBreakoutScore(
                        scoreResult.finalScore(),
                        scoreResult.trendDirection(),
                        close,
                        prevClose,
                        volumeRatio,
                        breakoutConfirmed,
                        distToBreakoutPct,
                        rangePosition,
                        roc10Pct,
                        macdHist,
                        prevMacdHist,
                        rsi,
                        percentB);

        SetupCategory category =
                classifyCategory(
                        rsi,
                        percentB,
                        momentumBreakoutScore,
                        scoreResult.finalScore(),
                        scoreResult.trendDirection(),
                        rangePosition,
                        close,
                        supportWatchZone.high());

        List<String> rationaleItems =
                buildRationaleItems(
                        close,
                        volumeRatio,
                        rsi,
                        atr14,
                        atrPercent,
                        breakoutConfirmed,
                        scaledBreakoutTrigger,
                        distToBreakoutPct,
                        supportWatchZone,
                        resistanceExitZone,
                        invalidationRiskLevel,
                        riskReward,
                        bistMargin);

        List<String> riskWarnings =
                buildRiskWarnings(
                        rsi,
                        percentB,
                        atrPercent,
                        volumeRatio,
                        riskReward,
                        scoreResult.staleData(),
                        simulated,
                        close,
                        prevClose);

        String invalidationRationale =
                "Teknik destek alt bandı ("
                        + supportWatchZone.low()
                        + ") altında 0,50 × ATR(14) volatilite tamponu ile hesaplanan "
                        + "kural tabanlı geçersizleşme referansı.";

        String headline =
                buildHeadline(
                        symbol,
                        category,
                        momentumBreakoutScore,
                        scoreResult.finalScore(),
                        breakoutConfirmed);

        return new TechnicalSetupResult(
                ENGINE_VERSION,
                ScoreStatus.READY,
                category,
                scoreResult.staleData(),
                FinancialMath.scaleMoney(scoreResult.finalScore()),
                momentumBreakoutScore,
                scoreResult.regime(),
                scoreResult.trendDirection(),
                FinancialMath.scalePrice(close),
                FinancialMath.scaleMoney(rsi),
                FinancialMath.scaleMoney(volumeRatio),
                FinancialMath.scalePrice(atr14),
                atrPercent,
                scaledBreakoutTrigger,
                distToBreakoutPct,
                breakoutConfirmed,
                supportWatchZone,
                resistanceExitZone,
                invalidationRiskLevel,
                invalidationRationale,
                riskReward,
                bistMargin,
                STANDARD_INDICATORS,
                rationaleItems,
                riskWarnings,
                List.of(),
                headline,
                DISCLAIMER);
    }

    private static List<String> collectInsufficientReasons(
            List<OhlcvBar> orderedBars,
            Map<IndicatorType, IndicatorSeries> seriesMap,
            TechnicalScoreResult scoreResult) {
        List<String> reasons = new ArrayList<>();
        if (orderedBars.size() < 25) {
            reasons.add("Teknik kurulum analizi için en az 25 sıralı OHLCV barı gereklidir.");
        } else if (orderedBars.getLast().close().compareTo(ZERO) <= 0) {
            reasons.add("Son bar kapanış fiyatı pozitif değil.");
        }
        if (scoreResult.status() != ScoreStatus.READY) {
            reasons.addAll(scoreResult.insufficientDataReasons());
        }

        requireSeriesReady(seriesMap, IndicatorType.SMA, "value", "SMA(20)", reasons);
        requireSeriesReady(seriesMap, IndicatorType.EMA, "value", "EMA(20)", reasons);
        requireSeriesReady(seriesMap, IndicatorType.RSI, "value", "RSI(14)", reasons);
        requireSeriesReady(seriesMap, IndicatorType.BOLLINGER, "upper", "BOLLINGER(20,2)", reasons);

        IndicatorSeries macd = seriesMap.get(IndicatorType.MACD);
        if (macd == null || macd.status() != IndicatorStatus.READY || macd.points().size() < 2) {
            reasons.add("MACD(12,26,9) için en az 2 histogram örneği gereklidir.");
        }

        BigDecimal atr = extractLatestValue(seriesMap.get(IndicatorType.ATR), "value");
        if (atr == null || atr.compareTo(ZERO) <= 0) {
            reasons.add("ATR(14) serisi hazır ve pozitif olmalıdır.");
        }

        BigDecimal volRatio = extractLatestValue(seriesMap.get(IndicatorType.VOLUME_AVG), "ratio");
        if (volRatio == null || volRatio.compareTo(ZERO) <= 0) {
            reasons.add("Hacim ortalaması ve oranı (VOLUME_AVG) pozitif olmalıdır.");
        }
        return reasons.stream().distinct().toList();
    }

    private static void requireSeriesReady(
            Map<IndicatorType, IndicatorSeries> seriesMap,
            IndicatorType type,
            String requiredKey,
            String displayName,
            List<String> reasons) {
        IndicatorSeries series = seriesMap.get(type);
        if (series == null
                || series.status() != IndicatorStatus.READY
                || series.latestValues().get(requiredKey) == null) {
            reasons.add(displayName + " indikatör serisi hazır değil.");
        }
    }

    private static BigDecimal extractLatestValue(IndicatorSeries series, String key) {
        if (series == null || series.status() != IndicatorStatus.READY) {
            return null;
        }
        return series.latestValues().get(key);
    }

    @Override
    public BistMarginReference computeBistMargin(
            List<OhlcvBar> orderedBars, LatestQuote latestQuote) {
        BigDecimal refPrice = null;
        BigDecimal prevClose = null;

        if (latestQuote != null
                && latestQuote.price().compareTo(ZERO) > 0
                && latestQuote.previousClose().compareTo(ZERO) > 0) {
            refPrice = latestQuote.price();
            prevClose = latestQuote.previousClose();
        } else if (orderedBars.size() >= 2) {
            BigDecimal barClose = orderedBars.getLast().close();
            BigDecimal barPrevClose = orderedBars.get(orderedBars.size() - 2).close();
            if (barClose.compareTo(ZERO) > 0 && barPrevClose.compareTo(ZERO) > 0) {
                refPrice = barClose;
                prevClose = barPrevClose;
            }
        }

        if (refPrice == null || prevClose == null) {
            return null;
        }

        BigDecimal ceiling = FinancialMath.scalePrice(prevClose.multiply(BIST_CEILING_FACTOR, MC));
        BigDecimal floor = FinancialMath.scalePrice(prevClose.multiply(BIST_FLOOR_FACTOR, MC));
        BigDecimal dailyChangePct =
                refPrice.subtract(prevClose, MC).divide(prevClose, MC).multiply(HUNDRED, MC);
        BigDecimal distCeilingPct =
                ceiling.subtract(refPrice, MC).divide(refPrice, MC).multiply(HUNDRED, MC);
        BigDecimal distFloorPct =
                refPrice.subtract(floor, MC).divide(refPrice, MC).multiply(HUNDRED, MC);

        return new BistMarginReference(
                refPrice,
                prevClose,
                dailyChangePct,
                ceiling,
                floor,
                distCeilingPct,
                distFloorPct,
                BIST_MARGIN_NOTE);
    }

    private static BigDecimal computePriorResistance19(List<OhlcvBar> orderedBars) {
        int n = orderedBars.size();
        int start = Math.max(0, n - 20);
        int endExclusive = Math.max(start + 1, n - 1);
        BigDecimal priorMax = orderedBars.get(start).high();
        for (int i = start + 1; i < endExclusive; i++) {
            priorMax = priorMax.max(orderedBars.get(i).high());
        }
        return priorMax;
    }

    private static PriceZone buildSupportWatchZone(
            BigDecimal close,
            BigDecimal atr,
            BigDecimal support20,
            BigDecimal sma20,
            BigDecimal ema20,
            BigDecimal bbLower,
            BigDecimal bbMiddle) {
        BigDecimal minFloor =
                close.compareTo(new BigDecimal("0.10")) >= 0
                        ? ZERO_POINT_ZERO_ONE
                        : MIN_PRICE_UNIT;

        List<BigDecimal> anchors = List.of(support20, sma20, ema20, bbLower, bbMiddle);
        BigDecimal zoneHigh = null;
        for (BigDecimal candidate : anchors) {
            if (candidate != null
                    && candidate.compareTo(ZERO) > 0
                    && candidate.compareTo(close) <= 0) {
                zoneHigh = zoneHigh == null ? candidate : zoneHigh.max(candidate);
            }
        }

        BigDecimal rawLow;
        String rationale;
        if (zoneHigh != null) {
            rawLow = zoneHigh.subtract(atr.multiply(new BigDecimal("0.60"), MC), MC);
            if (support20.compareTo(zoneHigh) < 0
                    && support20.compareTo(
                                    zoneHigh.subtract(atr.multiply(new BigDecimal("1.50"), MC), MC))
                            >= 0) {
                rawLow = rawLow.min(support20);
            }
            rationale =
                    "Fiyat altındaki en yakın EMA(20)/SMA(20)/Bollinger/20-bar destek kümesi "
                            + "ile 0,60 × ATR(14) geri çekilme bandı baz alındı.";
        } else {
            zoneHigh = close.subtract(atr.multiply(new BigDecimal("0.50"), MC), MC);
            rawLow = close.subtract(atr.multiply(new BigDecimal("1.25"), MC), MC);
            rationale =
                    "Kapanış tüm temel ortalamaların altında seyrettiği için "
                            + "[Kapanış - 1,25×ATR .. Kapanış - 0,50×ATR] dinamik izleme bandı üretildi.";
        }

        zoneHigh = zoneHigh.min(close).max(minFloor);
        rawLow = rawLow.max(minFloor).min(zoneHigh);

        BigDecimal scaledLow = FinancialMath.scalePrice(rawLow);
        BigDecimal scaledHigh = FinancialMath.scalePrice(zoneHigh);
        if (scaledLow.compareTo(scaledHigh) > 0) {
            scaledLow = scaledHigh;
        }
        return new PriceZone(scaledLow, scaledHigh, "Teknik Destek / İzleme Bölgesi", rationale);
    }

    private static PriceZone buildResistanceExitZone(
            BigDecimal close, BigDecimal atr, BigDecimal resistance20, BigDecimal bbUpper) {
        BigDecimal r1Candidate = resistance20.max(bbUpper);
        BigDecimal minR1 = close.add(atr.multiply(new BigDecimal("0.35"), MC), MC);
        BigDecimal r1 = r1Candidate.compareTo(minR1) >= 0 ? r1Candidate : close.add(atr, MC);
        BigDecimal r2 = r1.add(atr.multiply(new BigDecimal("1.50"), MC), MC);

        BigDecimal scaledClose = FinancialMath.scalePrice(close);
        BigDecimal scaledR1 = FinancialMath.scalePrice(r1);
        if (scaledR1.compareTo(scaledClose) <= 0) {
            scaledR1 =
                    FinancialMath.scalePrice(close.multiply(new BigDecimal("1.02"), MC))
                            .max(scaledClose.add(ZERO_POINT_ZERO_ONE));
        }

        BigDecimal scaledR2 = FinancialMath.scalePrice(r2);
        if (scaledR2.compareTo(scaledR1) <= 0) {
            scaledR2 =
                    FinancialMath.scalePrice(
                            scaledR1.add(atr.max(ZERO_POINT_ZERO_ONE), MC));
        }

        String rationale =
                "R1: max(20 barlık direnç, Bollinger üst bandı, Kapanış+0,35×ATR); "
                        + "R2: R1 + 1,50×ATR(14) kâr realizasyonu referans bölgesi.";
        return new PriceZone(
                scaledR1, scaledR2, "Teknik Direnç / Kâr Realizasyonu Referans Bölgesi", rationale);
    }

    private static BigDecimal buildInvalidationRiskLevel(
            BigDecimal close, BigDecimal atr, BigDecimal supportLow) {
        BigDecimal rawStop = supportLow.subtract(atr.multiply(new BigDecimal("0.50"), MC), MC);
        if (rawStop.compareTo(ZERO) <= 0) {
            rawStop = close.multiply(new BigDecimal("0.92"), MC).max(MIN_PRICE_UNIT);
        }
        BigDecimal scaledStop = FinancialMath.scalePrice(rawStop);
        BigDecimal scaledClose = FinancialMath.scalePrice(close);
        if (scaledStop.compareTo(scaledClose) >= 0) {
            scaledStop =
                    FinancialMath.scalePrice(close.multiply(new BigDecimal("0.92"), MC))
                            .max(MIN_PRICE_UNIT);
        }
        if (scaledStop.compareTo(ZERO) <= 0) {
            scaledStop = MIN_PRICE_UNIT;
        }
        return scaledStop;
    }

    private static RiskRewardMetrics computeRiskReward(
            BigDecimal close, BigDecimal invalidation, BigDecimal r1, BigDecimal r2) {
        BigDecimal scaledClose = FinancialMath.scalePrice(close);
        BigDecimal riskAmount = scaledClose.subtract(invalidation, MC);
        if (riskAmount.compareTo(ZERO) <= 0) {
            riskAmount = MIN_PRICE_UNIT;
        }
        BigDecimal riskPct = riskAmount.divide(scaledClose, MC).multiply(HUNDRED, MC);
        BigDecimal upsideR1Pct =
                r1.subtract(scaledClose, MC).divide(scaledClose, MC).multiply(HUNDRED, MC);
        BigDecimal upsideR2Pct =
                r2.subtract(scaledClose, MC).divide(scaledClose, MC).multiply(HUNDRED, MC);
        BigDecimal rr1 = FinancialMath.divide(r1.subtract(scaledClose, MC), riskAmount, 2);
        BigDecimal rr2 = FinancialMath.divide(r2.subtract(scaledClose, MC), riskAmount, 2);
        return new RiskRewardMetrics(riskPct, upsideR1Pct, upsideR2Pct, rr1, rr2);
    }

    private static BigDecimal computeMomentumBreakoutScore(
            BigDecimal technicalScore,
            TrendDirection trendDirection,
            BigDecimal close,
            BigDecimal prevClose,
            BigDecimal volumeRatio,
            boolean breakoutConfirmed,
            BigDecimal distToBreakoutPct,
            BigDecimal rangePosition,
            BigDecimal roc10Pct,
            BigDecimal macdHist,
            BigDecimal prevMacdHist,
            BigDecimal rsi,
            BigDecimal percentB) {
        BigDecimal volumeSub;
        if (close.compareTo(prevClose) >= 0) {
            volumeSub = clamp(volumeRatio.multiply(new BigDecimal("55"), MC), ZERO, HUNDRED);
        } else {
            volumeSub =
                    clamp(
                            new BigDecimal("45")
                                    .subtract(volumeRatio.multiply(new BigDecimal("20"), MC), MC),
                            ZERO,
                            new BigDecimal("45"));
        }

        BigDecimal breakoutSub;
        if (breakoutConfirmed) {
            breakoutSub =
                    volumeRatio.compareTo(new BigDecimal("1.20")) >= 0
                            ? HUNDRED
                            : new BigDecimal("92");
        } else if (distToBreakoutPct.compareTo(new BigDecimal("2.0")) <= 0) {
            BigDecimal d = distToBreakoutPct.max(ZERO);
            breakoutSub = new BigDecimal("85").subtract(d.multiply(new BigDecimal("5"), MC), MC);
        } else if (distToBreakoutPct.compareTo(new BigDecimal("6.0")) <= 0) {
            BigDecimal excess = distToBreakoutPct.subtract(new BigDecimal("2.0"), MC);
            breakoutSub =
                    new BigDecimal("75").subtract(excess.multiply(new BigDecimal("7.5"), MC), MC);
        } else {
            breakoutSub =
                    clamp(rangePosition.multiply(new BigDecimal("60"), MC), ZERO, new BigDecimal("45"));
        }

        BigDecimal rocPart =
                clamp(
                        new BigDecimal("50").add(roc10Pct.multiply(new BigDecimal("4"), MC), MC),
                        ZERO,
                        HUNDRED);
        BigDecimal macdAdj;
        if (macdHist.compareTo(ZERO) > 0 && macdHist.compareTo(prevMacdHist) >= 0) {
            macdAdj = new BigDecimal("15");
        } else if (macdHist.compareTo(ZERO) > 0) {
            macdAdj = new BigDecimal("5");
        } else {
            macdAdj = new BigDecimal("-10");
        }
        BigDecimal velocitySub = clamp(rocPart.add(macdAdj, MC), ZERO, HUNDRED);

        BigDecimal alignmentSub =
                switch (trendDirection) {
                    case BULLISH -> new BigDecimal("90");
                    case NEUTRAL -> new BigDecimal("50");
                    case BEARISH -> new BigDecimal("15");
                    case UNKNOWN -> ZERO;
                };

        BigDecimal weighted =
                technicalScore
                        .multiply(new BigDecimal("0.35"), MC)
                        .add(volumeSub.multiply(new BigDecimal("0.25"), MC), MC)
                        .add(breakoutSub.multiply(new BigDecimal("0.20"), MC), MC)
                        .add(velocitySub.multiply(new BigDecimal("0.15"), MC), MC)
                        .add(alignmentSub.multiply(new BigDecimal("0.05"), MC), MC);

        BigDecimal penalty = ZERO;
        if (rsi.compareTo(new BigDecimal("78")) > 0) {
            BigDecimal rsiExcess =
                    rsi.subtract(new BigDecimal("78"), MC).multiply(new BigDecimal("1.5"), MC);
            penalty = penalty.add(rsiExcess.min(new BigDecimal("20")), MC);
        }
        if (percentB.compareTo(new BigDecimal("1.25")) > 0) {
            BigDecimal bbExcess =
                    percentB.subtract(new BigDecimal("1.25"), MC)
                            .multiply(new BigDecimal("20"), MC);
            penalty = penalty.add(bbExcess.min(new BigDecimal("10")), MC);
        }

        return FinancialMath.scaleMoney(clamp(weighted.subtract(penalty, MC), ZERO, HUNDRED));
    }

    private static SetupCategory classifyCategory(
            BigDecimal rsi,
            BigDecimal percentB,
            BigDecimal momentumBreakoutScore,
            BigDecimal technicalScore,
            TrendDirection trendDirection,
            BigDecimal rangePosition,
            BigDecimal close,
            BigDecimal supportHigh) {
        if (rsi.compareTo(new BigDecimal("75")) > 0
                || percentB.compareTo(new BigDecimal("1.20")) > 0) {
            return SetupCategory.OVEREXTENDED_RISK;
        }
        if (momentumBreakoutScore.compareTo(new BigDecimal("62")) >= 0
                && (trendDirection == TrendDirection.BULLISH
                        || technicalScore.compareTo(new BigDecimal("60")) >= 0)) {
            return SetupCategory.HIGH_MOMENTUM_BREAKOUT;
        }
        BigDecimal distAboveSupportPct =
                close.subtract(supportHigh, MC).divide(close, MC).multiply(HUNDRED, MC);
        if (technicalScore.compareTo(new BigDecimal("48")) >= 0
                && rsi.compareTo(new BigDecimal("32")) >= 0
                && rsi.compareTo(new BigDecimal("62")) <= 0
                && (rangePosition.compareTo(new BigDecimal("0.45")) <= 0
                        || distAboveSupportPct.compareTo(new BigDecimal("2.50")) <= 0)) {
            return SetupCategory.PULLBACK_WATCH;
        }
        if (technicalScore.compareTo(new BigDecimal("45")) < 0
                || (trendDirection == TrendDirection.BEARISH
                        && momentumBreakoutScore.compareTo(new BigDecimal("45")) < 0)) {
            return SetupCategory.WEAK_BEARISH;
        }
        return SetupCategory.CONSOLIDATION;
    }

    private static List<String> buildRationaleItems(
            BigDecimal close,
            BigDecimal volumeRatio,
            BigDecimal rsi,
            BigDecimal atr14,
            BigDecimal atrPercent,
            boolean breakoutConfirmed,
            BigDecimal breakoutTrigger,
            BigDecimal distToBreakoutPct,
            PriceZone supportWatchZone,
            PriceZone resistanceExitZone,
            BigDecimal invalidationRiskLevel,
            RiskRewardMetrics riskReward,
            BistMarginReference bistMargin) {
        List<String> items = new ArrayList<>();
        if (breakoutConfirmed) {
            items.add(
                    "Kapanış ("
                            + FinancialMath.scalePrice(close)
                            + "), önceki 19 barlık direnç eşiği ("
                            + breakoutTrigger
                            + ") üzerinde kırılım gerçekleştirdi.");
        } else {
            items.add(
                    "Önceki 19 barlık kırılım eşiği "
                            + breakoutTrigger
                            + " seviyesinde (eşiğe uzaklık %"
                            + FinancialMath.scaleMoney(distToBreakoutPct)
                            + ").");
        }
        items.add(
                "Hacim katılımı 20 barlık ortalamanın "
                        + FinancialMath.scaleMoney(volumeRatio)
                        + "x katı; RSI(14)="
                        + FinancialMath.scaleMoney(rsi)
                        + ", ATR(14)="
                        + FinancialMath.scalePrice(atr14)
                        + " (%"
                        + FinancialMath.scaleMoney(atrPercent)
                        + ").");
        items.add(
                "Teknik Destek / İzleme Bandı: ["
                        + supportWatchZone.low()
                        + " .. "
                        + supportWatchZone.high()
                        + "]; Risk Sınırı (Stop Ref.): "
                        + invalidationRiskLevel
                        + " (-%"
                        + FinancialMath.scaleMoney(riskReward.riskDistancePercent())
                        + ").");
        items.add(
                "Teknik Direnç / Kâr Realizasyonu Bölgesi: R1="
                        + resistanceExitZone.low()
                        + " (+%"
                        + FinancialMath.scaleMoney(riskReward.upsideToR1Percent())
                        + "), R2="
                        + resistanceExitZone.high()
                        + " (+%"
                        + FinancialMath.scaleMoney(riskReward.upsideToR2Percent())
                        + ") · Risk/Ödül 1 : "
                        + riskReward.riskRewardToR1()
                        + " (R1) / 1 : "
                        + riskReward.riskRewardToR2()
                        + " (R2).");
        if (bistMargin != null) {
            items.add(
                    "BIST ±%10 teorik günlük marj sınırları: Taban ref. "
                            + bistMargin.theoreticalFloorPrice()
                            + " · Tavan ref. "
                            + bistMargin.theoreticalCeilingPrice()
                            + " (teorik tavan sınırına uzaklık %"
                            + FinancialMath.scaleMoney(bistMargin.distanceToCeilingPercent())
                            + ").");
        }
        return items;
    }

    private static List<String> buildRiskWarnings(
            BigDecimal rsi,
            BigDecimal percentB,
            BigDecimal atrPercent,
            BigDecimal volumeRatio,
            RiskRewardMetrics riskReward,
            boolean staleData,
            boolean simulated,
            BigDecimal close,
            BigDecimal prevClose) {
        List<String> warnings = new ArrayList<>();
        if (rsi.compareTo(new BigDecimal("68")) > 0
                || percentB.compareTo(new BigDecimal("1.10")) > 0) {
            warnings.add(
                    "RSI(14)="
                            + FinancialMath.scaleMoney(rsi)
                            + " veya Bollinger konumu aşırı ısınma / sert düzeltme riskine işaret ediyor.");
        }
        if (atrPercent.compareTo(new BigDecimal("4.00")) > 0) {
            warnings.add(
                    "Günlük ortalama oynaklık (ATR %"
                            + FinancialMath.scaleMoney(atrPercent)
                            + ") yüksek; stop mesafesi ve pozisyon boyutu dikkatle değerlendirilmelidir.");
        }
        if (riskReward.riskRewardToR1().compareTo(BigDecimal.ONE) < 0) {
            warnings.add(
                    "Mevcut fiyattan R1 direnç referansına Risk/Ödül oranı (1:"
                            + riskReward.riskRewardToR1()
                            + ") 1,00 altında; destek bölgesine yakınlık takip edilebilir.");
        }
        if (close.compareTo(prevClose) > 0 && volumeRatio.compareTo(new BigDecimal("0.85")) < 0) {
            warnings.add(
                    "Yukarı yönlü kapanışa rağmen hacim 20 barlık ortalamanın altında ("
                            + FinancialMath.scaleMoney(volumeRatio)
                            + "x).");
        }
        if (staleData) {
            warnings.add("Dayanak fiyat verisi tazelik eşiğini aşmış olabilir.");
        }
        if (simulated) {
            warnings.add("Simüle/mock sağlayıcı verileri üzerinden üretilmiştir; gerçek piyasa değildir.");
        }
        return warnings;
    }

    private static String buildHeadline(
            String symbol,
            SetupCategory category,
            BigDecimal momentumScore,
            BigDecimal technicalScore,
            boolean breakoutConfirmed) {
        return switch (category) {
            case HIGH_MOMENTUM_BREAKOUT ->
                    symbol
                            + ": Momentum Skoru "
                            + momentumScore
                            + "/100 (Teknik "
                            + FinancialMath.scaleMoney(technicalScore)
                            + "/100) · "
                            + (breakoutConfirmed
                                    ? "19 barlık direnç üzerinde hacimli kırılım profili."
                                    : "Direnç eşiğine yakın güçlü momentum profili.");
            case PULLBACK_WATCH ->
                    symbol
                            + ": Momentum "
                            + momentumScore
                            + "/100 (Teknik "
                            + FinancialMath.scaleMoney(technicalScore)
                            + "/100) · Destek/ortalama bölgesinde geri çekilme izleme profili.";
            case OVEREXTENDED_RISK ->
                    symbol
                            + ": Momentum "
                            + momentumScore
                            + "/100 · Yüksek RSI/üst bant uzaması nedeniyle aşırı ısınma riski.";
            case WEAK_BEARISH ->
                    symbol
                            + ": Momentum "
                            + momentumScore
                            + "/100 · Zayıf teknik ve düşüş eğilimli momentum görünümü.";
            case CONSOLIDATION ->
                    symbol
                            + ": Momentum "
                            + momentumScore
                            + "/100 · Yatay/dengeli sıkışma bölgesi görünümü.";
            case INSUFFICIENT_DATA ->
                    symbol + ": Yetersiz veya bayat veri nedeniyle kurulum üretilmedi.";
        };
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
