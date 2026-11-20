package com.stockvision.scoring.api;

import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import com.stockvision.marketdata.api.FreshnessMetadataResponse;
import java.util.List;
import java.util.Map;

/**
 * Paginated rule-based momentum candidates and technical reference levels response.
 */
public record OpportunitiesPageResponse(
        String exchange,
        String interval,
        boolean adjusted,
        String setupEngineVersion,
        String scoringAlgorithmVersion,
        int page,
        int size,
        long totalElements,
        int totalPages,
        Map<String, Long> categoryCounts,
        String disclaimer,
        DataSourceMetadataResponse dataSource,
        FreshnessMetadataResponse freshness,
        List<OpportunityItemResponse> items) {
}
