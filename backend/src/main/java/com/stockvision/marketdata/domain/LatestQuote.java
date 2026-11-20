package com.stockvision.marketdata.domain;

import com.stockvision.common.domain.FinancialMath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Latest quote snapshot produced by a {@link MarketDataProvider}.
 */
public record LatestQuote(
        String symbol,
        BigDecimal price,
        BigDecimal previousClose,
        BigDecimal changeAmount,
        BigDecimal changePercent,
        long volume,
        String currency,
        Instant quotedAt,
        String provider,
        boolean simulated,
        int delayMinutes,
        String sourceTimezone) {

    public LatestQuote {
        Objects.requireNonNull(symbol, "symbol must not be null");
        price = FinancialMath.scalePrice(Objects.requireNonNull(price, "price must not be null"));
        previousClose =
                FinancialMath.scalePrice(
                        Objects.requireNonNull(previousClose, "previousClose must not be null"));
        changeAmount =
                FinancialMath.scalePrice(
                        Objects.requireNonNull(changeAmount, "changeAmount must not be null"));
        changePercent =
                FinancialMath.scalePercentage(
                        Objects.requireNonNull(changePercent, "changePercent must not be null"));
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(quotedAt, "quotedAt must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(sourceTimezone, "sourceTimezone must not be null");
        if (volume < 0L) {
            throw new IllegalArgumentException("volume must not be negative");
        }
        if (delayMinutes < 0) {
            throw new IllegalArgumentException("delayMinutes must not be negative");
        }
    }
}
