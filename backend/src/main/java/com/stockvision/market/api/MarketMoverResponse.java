package com.stockvision.market.api;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Summary mover quote row inside {@link MarketSummaryResponse}.
 */
public record MarketMoverResponse(
        String symbol,
        String name,
        BigDecimal price,
        BigDecimal changeAmount,
        BigDecimal changePercent,
        long volume,
        String currency,
        Instant quotedAt,
        boolean simulated) {
}
