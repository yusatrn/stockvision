package com.stockvision.portfolio.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregate derived position ledger snapshot across all traded instruments in a portfolio.
 */
public record LedgerCalculationResult(
        Map<UUID, DerivedPosition> positions,
        BigDecimal totalCostBasis,
        BigDecimal totalRealizedPnl,
        BigDecimal totalFees,
        BigDecimal totalTaxes) {
}
