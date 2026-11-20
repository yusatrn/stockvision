package com.stockvision.marketdata.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.marketdata.domain.DataProviderMode;
import com.stockvision.marketdata.domain.LatestQuote;
import com.stockvision.marketdata.domain.OhlcvBar;
import com.stockvision.marketdata.domain.ProviderMetadata;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockMarketDataProviderTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-19T13:00:00Z");
    private static final ZoneId BIST_ZONE = ZoneId.of("Europe/Istanbul");

    private final MockMarketDataProvider provider =
            new MockMarketDataProvider(Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

    @Test
    void metadataExplicitlyLabelsDataAsSimulated() {
        ProviderMetadata metadata = provider.getProviderMetadata();

        assertThat(metadata.providerId()).isEqualTo(MockMarketDataProvider.PROVIDER_ID);
        assertThat(metadata.mode()).isEqualTo(DataProviderMode.SIMULATED);
        assertThat(metadata.simulated()).isTrue();
        assertThat(metadata.sourceTimezone()).isEqualTo("Europe/Istanbul");
    }

    @Test
    void producesDeterministicRepeatableBarsAcrossOverlappingRanges() {
        Instant wideFrom = Instant.parse("2026-10-01T00:00:00Z");
        Instant wideTo = Instant.parse("2026-11-19T12:00:00Z");
        Instant subFrom = Instant.parse("2026-10-20T00:00:00Z");
        Instant subTo = Instant.parse("2026-11-10T23:59:59Z");

        List<OhlcvBar> firstCall =
                provider.getHistoricalBars("THYAO", BarInterval.ONE_DAY, wideFrom, wideTo, false);
        List<OhlcvBar> secondCall =
                provider.getHistoricalBars("THYAO", BarInterval.ONE_DAY, wideFrom, wideTo, false);
        List<OhlcvBar> subRange =
                provider.getHistoricalBars("THYAO", BarInterval.ONE_DAY, subFrom, subTo, false);

        assertThat(firstCall).isNotEmpty().isEqualTo(secondCall);
        assertThat(firstCall).allMatch(OhlcvBar::simulated);

        List<OhlcvBar> overlappingFromWide =
                firstCall.stream()
                        .filter(
                                bar ->
                                        !bar.timestamp().isBefore(subFrom)
                                                && !bar.timestamp().isAfter(subTo))
                        .toList();
        assertThat(subRange).isNotEmpty().isEqualTo(overlappingFromWide);
    }

    @Test
    void omitsWeekendsAndFixedBistHolidays() {
        Instant from = Instant.parse("2026-10-23T00:00:00Z");
        Instant to = Instant.parse("2026-11-02T23:59:59Z");

        List<OhlcvBar> bars =
                provider.getHistoricalBars("GARAN", BarInterval.ONE_DAY, from, to, false);

        List<LocalDate> dates =
                bars.stream()
                        .map(
                                bar ->
                                        ZonedDateTime.ofInstant(bar.timestamp(), BIST_ZONE)
                                                .toLocalDate())
                        .toList();

        assertThat(dates)
                .doesNotContain(
                        LocalDate.of(2026, 10, 24),
                        LocalDate.of(2026, 10, 25),
                        LocalDate.of(2026, 10, 29),
                        LocalDate.of(2026, 10, 31),
                        LocalDate.of(2026, 11, 1));
    }

    @Test
    void separatesAdjustedAndUnadjustedSeriesAndProducesSimulatedQuote() {
        Instant from = Instant.parse("2025-06-15T00:00:00Z");
        Instant to = Instant.parse("2025-07-10T23:59:59Z");

        List<OhlcvBar> unadjusted =
                provider.getHistoricalBars("AKBNK", BarInterval.ONE_DAY, from, to, false);
        List<OhlcvBar> adjusted =
                provider.getHistoricalBars("AKBNK", BarInterval.ONE_DAY, from, to, true);

        assertThat(unadjusted).hasSameSizeAs(adjusted);
        assertThat(unadjusted.getFirst().adjusted()).isFalse();
        assertThat(adjusted.getFirst().adjusted()).isTrue();
        assertThat(adjusted.getFirst().close()).isLessThan(unadjusted.getFirst().close());
        assertThat(adjusted.getLast().close()).isEqualByComparingTo(unadjusted.getLast().close());

        LatestQuote quote = provider.getLatestQuote("AKBNK");
        assertThat(quote.simulated()).isTrue();
        assertThat(quote.provider()).isEqualTo(MockMarketDataProvider.PROVIDER_ID);
        assertThat(quote.price()).isPositive();
    }
}
