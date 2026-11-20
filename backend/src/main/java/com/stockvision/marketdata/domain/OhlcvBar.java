package com.stockvision.marketdata.domain;

import com.stockvision.common.domain.FinancialMath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable OHLCV market data bar enforcing monetary rounding and strict price/volume invariants.
 */
public record OhlcvBar(
        Instant timestamp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        String currency,
        BarInterval interval,
        String provider,
        boolean adjusted,
        boolean simulated,
        String sourceTimezone,
        Instant ingestedAt) {

    public OhlcvBar {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(open, "open must not be null");
        Objects.requireNonNull(high, "high must not be null");
        Objects.requireNonNull(low, "low must not be null");
        Objects.requireNonNull(close, "close must not be null");
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        Objects.requireNonNull(interval, "interval must not be null");
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        if (sourceTimezone == null || sourceTimezone.isBlank()) {
            throw new IllegalArgumentException("sourceTimezone must not be blank");
        }
        Objects.requireNonNull(ingestedAt, "ingestedAt must not be null");

        open = FinancialMath.scalePrice(open);
        high = FinancialMath.scalePrice(high);
        low = FinancialMath.scalePrice(low);
        close = FinancialMath.scalePrice(close);

        if (open.compareTo(BigDecimal.ZERO) <= 0
                || high.compareTo(BigDecimal.ZERO) <= 0
                || low.compareTo(BigDecimal.ZERO) <= 0
                || close.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("OHLC prices must be strictly positive");
        }
        if (high.compareTo(open) < 0
                || high.compareTo(close) < 0
                || high.compareTo(low) < 0) {
            throw new IllegalArgumentException("high price must be >= open, close, and low");
        }
        if (low.compareTo(open) > 0
                || low.compareTo(close) > 0
                || low.compareTo(high) > 0) {
            throw new IllegalArgumentException("low price must be <= open, close, and high");
        }
        if (volume < 0L) {
            throw new IllegalArgumentException("volume must not be negative");
        }
    }
}
