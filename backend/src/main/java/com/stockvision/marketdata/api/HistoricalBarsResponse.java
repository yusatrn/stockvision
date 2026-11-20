package com.stockvision.marketdata.api;

import java.time.Instant;
import java.util.List;

/**
 * Historical OHLCV bar series response including source, freshness, and missing-data notes.
 */
public record HistoricalBarsResponse(
        String symbol,
        String exchange,
        String interval,
        boolean adjusted,
        Instant from,
        Instant to,
        int count,
        String missingDataNote,
        DataSourceMetadataResponse dataSource,
        FreshnessMetadataResponse freshness,
        List<OhlcvBarResponse> bars) {
}
