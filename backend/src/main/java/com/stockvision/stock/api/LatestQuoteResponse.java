package com.stockvision.stock.api;

import com.stockvision.marketdata.domain.LatestQuote;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Serialized latest quote snapshot explicitly indicating simulation status and delay.
 */
public record LatestQuoteResponse(
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

    public static LatestQuoteResponse from(LatestQuote quote) {
        return new LatestQuoteResponse(
                quote.price(),
                quote.previousClose(),
                quote.changeAmount(),
                quote.changePercent(),
                quote.volume(),
                quote.currency(),
                quote.quotedAt(),
                quote.provider(),
                quote.simulated(),
                quote.delayMinutes(),
                quote.sourceTimezone());
    }
}
