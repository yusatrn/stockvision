package com.stockvision.portfolio.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Derived open holding valuation computed exclusively from chronological BUY/SELL ledger rows.
 */
public record PortfolioHoldingResponse(
        UUID instrumentId,
        String symbol,
        String name,
        String exchange,
        String currency,
        BigDecimal openQuantity,
        BigDecimal averageCost,
        BigDecimal costBasis,
        BigDecimal latestPrice,
        Instant quotedAt,
        boolean simulated,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        BigDecimal unrealizedPnlPercent,
        BigDecimal realizedPnl,
        BigDecimal totalFees,
        BigDecimal totalTaxes) {
}
