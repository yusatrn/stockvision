package com.stockvision.stock.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Framework-independent domain model for a tradable or tracked instrument.
 */
public record Instrument(
        UUID id,
        UUID exchangeId,
        String exchangeCode,
        String symbol,
        String name,
        String sector,
        String currency,
        InstrumentType type,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public Instrument {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(exchangeId, "exchangeId must not be null");
        Objects.requireNonNull(exchangeCode, "exchangeCode must not be null");
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }
}
