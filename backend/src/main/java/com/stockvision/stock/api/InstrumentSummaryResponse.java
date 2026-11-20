package com.stockvision.stock.api;

import com.stockvision.stock.domain.Instrument;
import com.stockvision.stock.domain.InstrumentType;
import java.util.UUID;

/**
 * Summary item returned by paginated instrument catalog queries.
 */
public record InstrumentSummaryResponse(
        UUID id,
        String symbol,
        String name,
        String exchange,
        String sector,
        String currency,
        InstrumentType type,
        boolean active) {

    public static InstrumentSummaryResponse from(Instrument instrument) {
        return new InstrumentSummaryResponse(
                instrument.id(),
                instrument.symbol(),
                instrument.name(),
                instrument.exchangeCode(),
                instrument.sector(),
                instrument.currency(),
                instrument.type(),
                instrument.active());
    }
}
