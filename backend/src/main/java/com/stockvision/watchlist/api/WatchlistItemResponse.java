package com.stockvision.watchlist.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Watchlist instrument entry with resolved instrument identity and optional quote snapshot.
 */
public record WatchlistItemResponse(
        UUID id,
        UUID instrumentId,
        String symbol,
        String name,
        String exchange,
        String sector,
        String currency,
        String notes,
        Instant createdAt,
        WatchlistItemQuoteResponse latestQuote) {
}
