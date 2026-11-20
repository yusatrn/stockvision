package com.stockvision.alert.api;

import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import com.stockvision.marketdata.api.FreshnessMetadataResponse;
import java.util.List;

/**
 * Collection of user alert rules with active market data source and freshness metadata.
 */
public record AlertRuleListResponse(
        List<AlertRuleResponse> alerts,
        DataSourceMetadataResponse dataSource,
        FreshnessMetadataResponse freshness) {
}
