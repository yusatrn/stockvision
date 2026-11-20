package com.stockvision.stock.api;

import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import java.util.List;

/**
 * Paginated instrument catalog response with source metadata.
 */
public record InstrumentPageResponse(
        List<InstrumentSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        DataSourceMetadataResponse dataSource) {
}
