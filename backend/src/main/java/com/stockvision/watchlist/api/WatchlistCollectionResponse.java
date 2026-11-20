package com.stockvision.watchlist.api;

import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import com.stockvision.marketdata.api.FreshnessMetadataResponse;
import java.util.List;

/**
 * User watchlist collection response accompanied by active market data source and freshness metadata.
 */
public record WatchlistCollectionResponse(
        List<WatchlistResponse> watchlists,
        DataSourceMetadataResponse dataSource,
        FreshnessMetadataResponse freshness) {
}
