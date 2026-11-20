package com.stockvision.marketdata.api;

import com.stockvision.marketdata.domain.OhlcvBar;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Serialized OHLCV bar payload. All timestamps are UTC and all prices use BigDecimal precision.
 */
public record OhlcvBarResponse(
        Instant timestamp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        String currency,
        String interval,
        String provider,
        boolean adjusted,
        boolean simulated,
        String sourceTimezone,
        Instant ingestedAt) {

    public static OhlcvBarResponse from(OhlcvBar bar) {
        return new OhlcvBarResponse(
                bar.timestamp(),
                bar.open(),
                bar.high(),
                bar.low(),
                bar.close(),
                bar.volume(),
                bar.currency(),
                bar.interval().code(),
                bar.provider(),
                bar.adjusted(),
                bar.simulated(),
                bar.sourceTimezone(),
                bar.ingestedAt());
    }
}
