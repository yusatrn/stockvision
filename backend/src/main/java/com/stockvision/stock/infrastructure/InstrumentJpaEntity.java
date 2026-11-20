package com.stockvision.stock.infrastructure;

import com.stockvision.common.domain.AuditableEntity;
import com.stockvision.market.infrastructure.ExchangeJpaEntity;
import com.stockvision.stock.domain.Instrument;
import com.stockvision.stock.domain.InstrumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * JPA entity mapping the {@code instruments} table.
 */
@Entity
@Table(name = "instruments")
public class InstrumentJpaEntity extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exchange_id", nullable = false)
    private ExchangeJpaEntity exchange;

    @Column(name = "symbol", nullable = false, length = 24)
    private String symbol;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "sector", length = 80)
    private String sector;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", nullable = false, length = 32)
    private InstrumentType instrumentType;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "catalog_rank")
    private Integer catalogRank;

    protected InstrumentJpaEntity() {
    }

    public Instrument toDomain() {
        return new Instrument(
                id,
                exchange.getId(),
                exchange.getCode(),
                symbol,
                name,
                sector,
                currency,
                instrumentType,
                active,
                getCreatedAt(),
                getUpdatedAt());
    }
}
