package com.stockvision.marketdata.domain;

import java.time.Instant;
import java.util.List;

/**
 * Isolation port for external or simulated market data sources.
 */
public interface MarketDataProvider {

    List<OhlcvBar> getHistoricalBars(
            String symbol, BarInterval interval, Instant from, Instant to, boolean adjusted);

    default List<OhlcvBar> getHistoricalBars(
            String symbol, BarInterval interval, Instant from, Instant to) {
        return getHistoricalBars(symbol, interval, from, to, false);
    }

    LatestQuote getLatestQuote(String symbol);

    ProviderMetadata getProviderMetadata();
}
