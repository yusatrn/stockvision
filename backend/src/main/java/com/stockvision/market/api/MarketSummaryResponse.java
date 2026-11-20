package com.stockvision.market.api;

import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import com.stockvision.marketdata.api.FreshnessMetadataResponse;
import java.util.List;

/**
 * Exchange-level market overview explicitly indicating source simulation status and freshness.
 */
public record MarketSummaryResponse(
        ExchangeResponse exchange,
        long activeInstrumentCount,
        boolean simulated,
        String summaryNote,
        DataSourceMetadataResponse dataSource,
        FreshnessMetadataResponse freshness,
        List<MarketMoverResponse> movers) {
}
