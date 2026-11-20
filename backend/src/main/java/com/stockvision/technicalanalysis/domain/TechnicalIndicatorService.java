package com.stockvision.technicalanalysis.domain;

import com.stockvision.marketdata.domain.OhlcvBar;
import java.util.List;

/**
 * Deterministic calculation port accepting strictly ordered, duplicate-free OHLCV bars and
 * producing technical indicator series with explicit warm-up and sufficiency metadata.
 */
public interface TechnicalIndicatorService {

    String engineVersion();

    TechnicalAnalysisResult calculate(
            List<OhlcvBar> orderedBars,
            List<IndicatorType> requestedIndicators,
            IndicatorConfiguration configuration);
}
