package com.stockvision.technicalanalysis.api;

import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import com.stockvision.marketdata.api.FreshnessMetadataResponse;
import com.stockvision.technicalanalysis.domain.AnalysisRunStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Technical indicator calculation response carrying persisted run metadata, source/freshness
 * context, disclaimer, and requested indicator series.
 */
public record TechnicalIndicatorsResponse(
        UUID analysisRunId,
        String symbol,
        String exchange,
        String interval,
        boolean adjusted,
        Instant from,
        Instant to,
        int barCount,
        String engineVersion,
        AnalysisRunStatus status,
        Instant calculatedAt,
        String disclaimer,
        DataSourceMetadataResponse dataSource,
        FreshnessMetadataResponse freshness,
        List<IndicatorSeriesResponse> indicators) {
}
