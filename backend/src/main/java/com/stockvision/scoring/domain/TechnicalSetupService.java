package com.stockvision.scoring.domain;

import com.stockvision.marketdata.domain.LatestQuote;
import com.stockvision.marketdata.domain.OhlcvBar;
import com.stockvision.technicalanalysis.domain.TechnicalAnalysisResult;
import java.util.List;

/**
 * Pure domain contract for rule-based technical momentum setup and support/resistance/invalidation
 * level evaluation.
 */
public interface TechnicalSetupService {

    String engineVersion();

    TechnicalSetupResult analyze(
            String symbol,
            List<OhlcvBar> orderedBars,
            TechnicalAnalysisResult indicatorResult,
            TechnicalScoreResult scoreResult,
            LatestQuote latestQuote,
            boolean simulated);

    BistMarginReference computeBistMargin(List<OhlcvBar> orderedBars, LatestQuote latestQuote);
}
