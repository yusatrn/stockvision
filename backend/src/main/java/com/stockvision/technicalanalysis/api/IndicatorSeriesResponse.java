package com.stockvision.technicalanalysis.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stockvision.technicalanalysis.domain.IndicatorSeries;
import com.stockvision.technicalanalysis.domain.IndicatorStatus;
import com.stockvision.technicalanalysis.domain.IndicatorType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Indicator output series with configuration parameters, warm-up counts, and sufficiency status.
 */
public record IndicatorSeriesResponse(
        IndicatorType type,
        IndicatorStatus status,
        int warmUpBars,
        int requiredBars,
        int availableBars,
        Map<String, Object> parameters,
        @JsonInclude(JsonInclude.Include.ALWAYS) Map<String, BigDecimal> latestValues,
        List<IndicatorPointResponse> points) {

    public static IndicatorSeriesResponse from(IndicatorSeries series) {
        return new IndicatorSeriesResponse(
                series.type(),
                series.status(),
                series.warmUpBars(),
                series.requiredBars(),
                series.availableBars(),
                series.parameters(),
                series.latestValues(),
                series.points().stream().map(IndicatorPointResponse::from).toList());
    }
}
