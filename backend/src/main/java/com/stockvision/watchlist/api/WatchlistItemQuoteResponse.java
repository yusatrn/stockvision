package com.stockvision.watchlist.api;

import com.stockvision.marketdata.domain.LatestQuote;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Latest price snapshot attached to a watchlist item response.
 */
public record WatchlistItemQuoteResponse(
        BigDecimal price,
        BigDecimal changeAmount,
        BigDecimal changePercent,
        long volume,
        String currency,
        Instant quotedAt,
        boolean simulated) {

    public static WatchlistItemQuoteResponse from(LatestQuote quote) {
        return new WatchlistItemQuoteResponse(
                quote.price(),
                quote.changeAmount(),
                quote.changePercent(),
                quote.volume(),
                quote.currency(),
                quote.quotedAt(),
                quote.simulated());
    }
}
