package com.stockvision.marketdata.infrastructure;

import com.stockvision.common.domain.FinancialMath;
import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.marketdata.domain.DataProviderMode;
import com.stockvision.marketdata.domain.LatestQuote;
import com.stockvision.marketdata.domain.MarketDataProvider;
import com.stockvision.marketdata.domain.OhlcvBar;
import com.stockvision.marketdata.domain.ProviderMetadata;
import com.stockvision.stock.domain.SymbolNormalizer;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic simulated {@link MarketDataProvider} for BIST-oriented MVP development and testing.
 *
 * <p>All outputs are explicitly labeled {@code simulated = true} and must never be presented as live
 * market data. Calculations use {@link BigDecimal} exclusively with {@link FinancialMath} rounding.
 */
@Component
@ConditionalOnProperty(name = "app.marketdata.provider", havingValue = "mock", matchIfMissing = true)
public class MockMarketDataProvider implements MarketDataProvider {

    public static final String PROVIDER_ID = "MOCK_SIMULATED";
    private static final String DISPLAY_NAME = "StockVision Deterministik Simüle Sağlayıcı";
    private static final String SOURCE_TIMEZONE = "Europe/Istanbul";
    private static final ZoneId BIST_ZONE = ZoneId.of(SOURCE_TIMEZONE);
    private static final String DEFAULT_CURRENCY = "TRY";
    private static final LocalDate ANCHOR_DATE = LocalDate.of(2015, 1, 2);
    private static final LocalDate SPLIT_ADJUSTMENT_CUTOFF = LocalDate.of(2025, 7, 1);
    private static final BigDecimal ADJUSTMENT_FACTOR = new BigDecimal("0.95");
    private static final BigDecimal BASIS_POINT_DIVISOR = new BigDecimal("10000");
    private static final BigDecimal MIN_PRICE = new BigDecimal("1.0000");
    private static final BigDecimal MIN_WICK_FLOOR = new BigDecimal("0.5000");
    private static final LocalTime SESSION_OPEN = LocalTime.of(10, 0);
    private static final LocalTime SESSION_CLOSE = LocalTime.of(18, 0);
    private static final String LICENSE_NOTE =
            "Yalnızca geliştirme ve test amaçlı simüle deterministik piyasa verisidir. Gerçek borsa verisi değildir.";

    private static final Set<MonthDay> FIXED_BIST_HOLIDAYS =
            Set.of(
                    MonthDay.of(1, 1),
                    MonthDay.of(4, 23),
                    MonthDay.of(5, 1),
                    MonthDay.of(5, 19),
                    MonthDay.of(7, 15),
                    MonthDay.of(8, 30),
                    MonthDay.of(10, 29));

    private final Clock clock;

    public MockMarketDataProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public ProviderMetadata getProviderMetadata() {
        return new ProviderMetadata(
                PROVIDER_ID,
                DISPLAY_NAME,
                DataProviderMode.SIMULATED,
                true,
                0,
                LICENSE_NOTE,
                SOURCE_TIMEZONE);
    }

    @Override
    public List<OhlcvBar> getHistoricalBars(
            String symbol, BarInterval interval, Instant from, Instant to, boolean adjusted) {
        Objects.requireNonNull(interval, "interval must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be strictly before to");
        }

        String normalizedSymbol = SymbolNormalizer.normalize(symbol);
        Instant now = clock.instant();
        Instant effectiveTo = to.isAfter(now) ? now : to;
        if (!from.isBefore(effectiveTo)) {
            return List.of();
        }

        LocalDate startDate = ZonedDateTime.ofInstant(from, BIST_ZONE).toLocalDate();
        LocalDate endDate = ZonedDateTime.ofInstant(effectiveTo, BIST_ZONE).toLocalDate();
        if (endDate.isBefore(ANCHOR_DATE)) {
            return List.of();
        }

        long symbolSeed = deriveSymbolSeed(normalizedSymbol);
        BigDecimal runningClose =
                FinancialMath.scalePrice(BigDecimal.valueOf(50L + Math.floorMod(symbolSeed, 350L)));

        List<OhlcvBar> bars = new ArrayList<>();
        LocalDate cursor = ANCHOR_DATE;
        while (!cursor.isAfter(endDate)) {
            if (isTradingDay(cursor)) {
                DailyRawBar daily = generateRawDailyBar(symbolSeed, cursor, runningClose);
                runningClose = daily.close();

                if (!cursor.isBefore(startDate)) {
                    if (interval == BarInterval.ONE_DAY) {
                        Instant barTimestamp =
                                cursor.atTime(SESSION_CLOSE).atZone(BIST_ZONE).toInstant();
                        if (!barTimestamp.isBefore(from) && !barTimestamp.isAfter(effectiveTo)) {
                            bars.add(
                                    toOhlcvBar(
                                            barTimestamp,
                                            daily.open(),
                                            daily.high(),
                                            daily.low(),
                                            daily.close(),
                                            daily.volume(),
                                            cursor,
                                            BarInterval.ONE_DAY,
                                            adjusted,
                                            now));
                        }
                    } else {
                        appendIntradayBars(
                                bars,
                                symbolSeed,
                                cursor,
                                daily,
                                interval,
                                from,
                                effectiveTo,
                                adjusted,
                                now);
                    }
                }
            }
            cursor = cursor.plusDays(1);
        }

        return List.copyOf(bars);
    }

    @Override
    public LatestQuote getLatestQuote(String symbol) {
        String normalizedSymbol = SymbolNormalizer.normalize(symbol);
        Instant now = clock.instant();
        Instant lookbackStart = now.minusSeconds(21L * 24L * 3600L);
        List<OhlcvBar> recentDailyBars =
                getHistoricalBars(normalizedSymbol, BarInterval.ONE_DAY, lookbackStart, now, false);
        if (recentDailyBars.size() < 2) {
            throw new IllegalStateException(
                    "Unable to compute simulated quote for symbol " + normalizedSymbol);
        }

        OhlcvBar previousBar = recentDailyBars.get(recentDailyBars.size() - 2);
        OhlcvBar latestBar = recentDailyBars.get(recentDailyBars.size() - 1);
        BigDecimal changeAmount =
                FinancialMath.scalePrice(latestBar.close().subtract(previousBar.close()));
        BigDecimal changePercent =
                FinancialMath.divide(
                        changeAmount.multiply(BigDecimal.valueOf(100)),
                        previousBar.close(),
                        FinancialMath.PERCENTAGE_SCALE);

        return new LatestQuote(
                normalizedSymbol,
                latestBar.close(),
                previousBar.close(),
                changeAmount,
                changePercent,
                latestBar.volume(),
                DEFAULT_CURRENCY,
                latestBar.timestamp(),
                PROVIDER_ID,
                true,
                0,
                SOURCE_TIMEZONE);
    }

    private void appendIntradayBars(
            List<OhlcvBar> collector,
            long symbolSeed,
            LocalDate tradingDate,
            DailyRawBar daily,
            BarInterval interval,
            Instant from,
            Instant effectiveTo,
            boolean adjusted,
            Instant ingestedAt) {
        int slotMinutes = interval == BarInterval.ONE_HOUR ? 60 : 15;
        int slots = interval == BarInterval.ONE_HOUR ? 8 : 32;
        long baseSeed = daySeed(symbolSeed, tradingDate);
        SplittableRandom rng = new SplittableRandom(baseSeed ^ (slots * 31L));

        BigDecimal totalDelta = daily.close().subtract(daily.open());
        BigDecimal slotCountBd = BigDecimal.valueOf(slots);
        BigDecimal currentOpen = daily.open();
        long slotVolumeBase = Math.max(1L, daily.volume() / slots);

        for (int idx = 1; idx <= slots; idx++) {
            LocalTime slotCloseTime = SESSION_OPEN.plusMinutes((long) idx * slotMinutes);
            Instant slotTimestamp = tradingDate.atTime(slotCloseTime).atZone(BIST_ZONE).toInstant();

            BigDecimal slotClose;
            if (idx == slots) {
                slotClose = daily.close();
            } else {
                BigDecimal progress =
                        FinancialMath.divide(
                                BigDecimal.valueOf(idx), slotCountBd, FinancialMath.INDICATOR_SCALE);
                BigDecimal baseline =
                        FinancialMath.scalePrice(daily.open().add(totalDelta.multiply(progress)));
                int noiseBps = rng.nextInt(-35, 36);
                slotClose = applyBps(baseline, noiseBps);
                if (slotClose.compareTo(MIN_PRICE) < 0) {
                    slotClose = MIN_PRICE;
                }
            }

            BigDecimal maxOc = currentOpen.max(slotClose);
            BigDecimal minOc = currentOpen.min(slotClose);
            BigDecimal slotHigh = applyBps(maxOc, rng.nextInt(2, 26)).max(maxOc);
            BigDecimal slotLow = applyBps(minOc, -rng.nextInt(2, 26)).max(MIN_WICK_FLOOR).min(minOc);
            long slotVolume = Math.max(0L, slotVolumeBase + rng.nextLong(0L, slotVolumeBase / 4 + 1));

            if (!slotTimestamp.isBefore(from) && !slotTimestamp.isAfter(effectiveTo)) {
                collector.add(
                        toOhlcvBar(
                                slotTimestamp,
                                currentOpen,
                                slotHigh,
                                slotLow,
                                slotClose,
                                slotVolume,
                                tradingDate,
                                interval,
                                adjusted,
                                ingestedAt));
            }
            currentOpen = slotClose;
        }
    }

    private OhlcvBar toOhlcvBar(
            Instant timestamp,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            long volume,
            LocalDate tradingDate,
            BarInterval interval,
            boolean adjusted,
            Instant ingestedAt) {
        BigDecimal finalOpen = open;
        BigDecimal finalHigh = high;
        BigDecimal finalLow = low;
        BigDecimal finalClose = close;

        if (adjusted && tradingDate.isBefore(SPLIT_ADJUSTMENT_CUTOFF)) {
            finalOpen = FinancialMath.scalePrice(open.multiply(ADJUSTMENT_FACTOR)).max(MIN_PRICE);
            finalClose = FinancialMath.scalePrice(close.multiply(ADJUSTMENT_FACTOR)).max(MIN_PRICE);
            BigDecimal maxOc = finalOpen.max(finalClose);
            BigDecimal minOc = finalOpen.min(finalClose);
            finalHigh = FinancialMath.scalePrice(high.multiply(ADJUSTMENT_FACTOR)).max(maxOc);
            finalLow =
                    FinancialMath.scalePrice(low.multiply(ADJUSTMENT_FACTOR))
                            .max(MIN_WICK_FLOOR)
                            .min(minOc);
        }

        return new OhlcvBar(
                timestamp,
                finalOpen,
                finalHigh,
                finalLow,
                finalClose,
                volume,
                DEFAULT_CURRENCY,
                interval,
                PROVIDER_ID,
                adjusted,
                true,
                SOURCE_TIMEZONE,
                ingestedAt);
    }

    private static DailyRawBar generateRawDailyBar(
            long symbolSeed, LocalDate date, BigDecimal previousClose) {
        SplittableRandom rng = new SplittableRandom(daySeed(symbolSeed, date));
        int gapBps = rng.nextInt(-55, 56);
        BigDecimal open = applyBps(previousClose, gapBps).max(MIN_PRICE);

        int moveBps = rng.nextInt(-260, 271);
        BigDecimal close = applyBps(open, moveBps).max(MIN_PRICE);

        BigDecimal maxOc = open.max(close);
        BigDecimal minOc = open.min(close);
        BigDecimal high = applyBps(maxOc, rng.nextInt(5, 130)).max(maxOc);
        BigDecimal low = applyBps(minOc, -rng.nextInt(5, 130)).max(MIN_WICK_FLOOR).min(minOc);
        long volume = 500_000L + rng.nextLong(4_500_000L);

        return new DailyRawBar(open, high, low, close, volume);
    }

    private static BigDecimal applyBps(BigDecimal base, int bps) {
        BigDecimal multiplier = BigDecimal.valueOf(10_000L + bps);
        return base.multiply(multiplier)
                .divide(BASIS_POINT_DIVISOR, FinancialMath.PRICE_SCALE, FinancialMath.ROUNDING_MODE);
    }

    static boolean isTradingDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        return !FIXED_BIST_HOLIDAYS.contains(MonthDay.from(date));
    }

    private static long daySeed(long symbolSeed, LocalDate date) {
        return symbolSeed ^ (date.toEpochDay() * 0x9E3779B97F4A7C15L);
    }

    private static long deriveSymbolSeed(String normalizedSymbol) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash =
                    digest.digest(
                            (normalizedSymbol + "|STOCKVISION_MOCK_V1")
                                    .getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(hash).getLong();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }

    private record DailyRawBar(
            BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {
    }
}
