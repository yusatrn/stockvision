package com.stockvision.portfolio.api;

import com.stockvision.portfolio.domain.TransactionSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable ledger transaction response including instrument identity and gross notional amount.
 */
public record PortfolioTransactionResponse(
        UUID id,
        UUID instrumentId,
        String symbol,
        String instrumentName,
        TransactionSide side,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal grossAmount,
        BigDecimal feeAmount,
        BigDecimal taxAmount,
        String currency,
        Instant executedAt,
        String notes,
        Instant createdAt) {
}
