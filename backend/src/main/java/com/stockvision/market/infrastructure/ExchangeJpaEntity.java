package com.stockvision.market.infrastructure;

import com.stockvision.common.domain.AuditableEntity;
import com.stockvision.market.domain.Exchange;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

/**
 * JPA entity mapping the {@code exchanges} table.
 */
@Entity
@Table(name = "exchanges")
public class ExchangeJpaEntity extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 16)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "session_open_local", nullable = false)
    private LocalTime sessionOpenLocal;

    @Column(name = "session_close_local", nullable = false)
    private LocalTime sessionCloseLocal;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected ExchangeJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public Exchange toDomain() {
        return new Exchange(
                id,
                code,
                name,
                countryCode,
                currency,
                timezone,
                sessionOpenLocal,
                sessionCloseLocal,
                active,
                getCreatedAt(),
                getUpdatedAt());
    }
}
