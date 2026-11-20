package com.stockvision.portfolio.infrastructure;

import com.stockvision.common.domain.AuditableEntity;
import com.stockvision.portfolio.domain.FeeTaxPolicy;
import com.stockvision.portfolio.domain.Portfolio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * JPA entity mapping the {@code portfolios} table.
 */
@Entity
@Table(name = "portfolios")
public class PortfolioJpaEntity extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "base_currency", nullable = false, length = 8)
    private String baseCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_tax_policy", nullable = false, length = 32)
    private FeeTaxPolicy feeTaxPolicy;

    protected PortfolioJpaEntity() {
    }

    public static PortfolioJpaEntity fromDomain(Portfolio portfolio) {
        PortfolioJpaEntity entity = new PortfolioJpaEntity();
        entity.id = portfolio.id();
        entity.userId = portfolio.userId();
        entity.name = portfolio.name();
        entity.description = portfolio.description();
        entity.baseCurrency = portfolio.baseCurrency();
        entity.feeTaxPolicy = portfolio.feeTaxPolicy();
        return entity;
    }

    public Portfolio toDomain() {
        return new Portfolio(
                id,
                userId,
                name,
                description,
                baseCurrency,
                feeTaxPolicy,
                getCreatedAt(),
                getUpdatedAt());
    }
}
