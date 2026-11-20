package com.stockvision.stock.api;

import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import com.stockvision.marketdata.api.FreshnessMetadataResponse;
import com.stockvision.stock.domain.InstrumentType;
import java.util.UUID;

/**
 * Detailed instrument metadata paired with the latest quote, data source, and freshness status.
 */
public record InstrumentDetailResponse(
        UUID id,
        String symbol,
        String name,
        String exchangeCode,
        String exchangeName,
        String exchangeTimezone,
        String sector,
        String currency,
        InstrumentType type,
        boolean active,
        LatestQuoteResponse latestQuote,
        DataSourceMetadataResponse dataSource,
        FreshnessMetadataResponse freshness) {
}
