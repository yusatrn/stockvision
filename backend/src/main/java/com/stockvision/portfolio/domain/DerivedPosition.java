package com.stockvision.portfolio.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Derived instrument position state computed strictly from the chronological transaction ledger.
 */
public record DerivedPosition(
        UUID instrumentId,
        BigDecimal openQuantity,
        BigDecimal averageCost,
        BigDecimal costBasis,
        BigDecimal realizedPnl,
        BigDecimal totalFees,
        BigDecimal totalTaxes,
        int buyCount,
        int sellCount) {
}
