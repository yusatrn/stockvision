package com.stockvision.portfolio.domain;

import com.stockvision.common.domain.FinancialMath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable ledger entry representing a virtual BUY or SELL execution.
 */
public record PortfolioTransaction(
        UUID id,
        UUID portfolioId,
        UUID instrumentId,
        TransactionSide side,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal feeAmount,
        BigDecimal taxAmount,
        String currency,
        Instant executedAt,
        String notes,
        Instant createdAt) {

    public PortfolioTransaction {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(portfolioId, "portfolioId must not be null");
        Objects.requireNonNull(instrumentId, "instrumentId must not be null");
        Objects.requireNonNull(side, "side must not be null");
        quantity =
                FinancialMath.scaleQuantity(
                        Objects.requireNonNull(quantity, "quantity must not be null"));
        unitPrice =
                FinancialMath.scalePrice(
                        Objects.requireNonNull(unitPrice, "unitPrice must not be null"));
        feeAmount =
                FinancialMath.scaleMoney(feeAmount != null ? feeAmount : BigDecimal.ZERO);
        taxAmount =
                FinancialMath.scaleMoney(taxAmount != null ? taxAmount : BigDecimal.ZERO);
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(executedAt, "executedAt must not be null");

        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("unitPrice must be greater than zero");
        }
        if (feeAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("feeAmount must not be negative");
        }
        if (taxAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("taxAmount must not be negative");
        }
    }
}
