package com.stockvision.market.domain;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Framework-independent domain model for a financial exchange and its local trading session.
 */
public record Exchange(
        UUID id,
        String code,
        String name,
        String countryCode,
        String currency,
        String timezone,
        LocalTime sessionOpenLocal,
        LocalTime sessionCloseLocal,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public Exchange {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(countryCode, "countryCode must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(timezone, "timezone must not be null");
        Objects.requireNonNull(sessionOpenLocal, "sessionOpenLocal must not be null");
        Objects.requireNonNull(sessionCloseLocal, "sessionCloseLocal must not be null");
    }
}
