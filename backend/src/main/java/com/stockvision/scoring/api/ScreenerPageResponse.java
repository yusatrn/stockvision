package com.stockvision.scoring.api;

import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import com.stockvision.marketdata.api.FreshnessMetadataResponse;
import java.util.List;

/**
 * Paginated screener response with data source, freshness metadata, and descriptive disclaimer.
 */
public record ScreenerPageResponse(
        String exchange,
        String interval,
        boolean adjusted,
        String algorithmVersion,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String disclaimer,
        DataSourceMetadataResponse dataSource,
        FreshnessMetadataResponse freshness,
        List<ScreenerItemResponse> items) {
}
