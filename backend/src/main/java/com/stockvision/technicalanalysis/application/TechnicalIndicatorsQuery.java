package com.stockvision.technicalanalysis.application;

import com.stockvision.technicalanalysis.domain.IndicatorConfiguration;
import java.time.Instant;
import java.util.List;

/**
 * Query command object for calculating and recording technical indicators on an instrument.
 */
public record TechnicalIndicatorsQuery(
        String symbol,
        String interval,
        List<String> indicators,
        Instant from,
        Instant to,
        boolean adjusted,
        IndicatorConfiguration configuration) {
}
