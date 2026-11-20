package com.stockvision.market.api;

import com.stockvision.market.domain.Exchange;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Public exchange metadata including local trading session hours and timezone.
 */
public record ExchangeResponse(
        UUID id,
        String code,
        String name,
        String countryCode,
        String currency,
        String timezone,
        LocalTime sessionOpenLocal,
        LocalTime sessionCloseLocal,
        boolean active) {

    public static ExchangeResponse from(Exchange exchange) {
        return new ExchangeResponse(
                exchange.id(),
                exchange.code(),
                exchange.name(),
                exchange.countryCode(),
                exchange.currency(),
                exchange.timezone(),
                exchange.sessionOpenLocal(),
                exchange.sessionCloseLocal(),
                exchange.active());
    }
}
