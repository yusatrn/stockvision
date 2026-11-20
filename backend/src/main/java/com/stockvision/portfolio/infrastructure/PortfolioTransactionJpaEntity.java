package com.stockvision.portfolio.infrastructure;

import com.stockvision.portfolio.domain.PortfolioTransaction;
import com.stockvision.portfolio.domain.TransactionSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the immutable {@code portfolio_transactions} ledger table.
 */
@Entity
@Table(name = "portfolio_transactions")
public class PortfolioTransactionJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "portfolio_id", nullable = false, updatable = false)
    private UUID portfolioId;

    @Column(name = "instrument_id", nullable = false, updatable = false)
    private UUID instrumentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, updatable = false, length = 8)
    private TransactionSide side;

    @Column(name = "quantity", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "fee_amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "tax_amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "currency", nullable = false, updatable = false, length = 8)
    private String currency;

    @Column(name = "executed_at", nullable = false, updatable = false)
    private Instant executedAt;

    @Column(name = "notes", updatable = false, length = 255)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PortfolioTransactionJpaEntity() {
    }

    public static PortfolioTransactionJpaEntity fromDomain(PortfolioTransaction tx) {
        PortfolioTransactionJpaEntity entity = new PortfolioTransactionJpaEntity();
        entity.id = tx.id();
        entity.portfolioId = tx.portfolioId();
        entity.instrumentId = tx.instrumentId();
        entity.side = tx.side();
        entity.quantity = tx.quantity();
        entity.unitPrice = tx.unitPrice();
        entity.feeAmount = tx.feeAmount();
        entity.taxAmount = tx.taxAmount();
        entity.currency = tx.currency();
        entity.executedAt = tx.executedAt();
        entity.notes = tx.notes();
        entity.createdAt = tx.createdAt() != null ? tx.createdAt() : Instant.now();
        return entity;
    }

    public PortfolioTransaction toDomain() {
        return new PortfolioTransaction(
                id,
                portfolioId,
                instrumentId,
                side,
                quantity,
                unitPrice,
                feeAmount,
                taxAmount,
                currency,
                executedAt,
                notes,
                createdAt);
    }
}
