package com.stockvision.scoring.domain;

import com.stockvision.marketdata.domain.OhlcvBar;
import com.stockvision.technicalanalysis.domain.TechnicalAnalysisResult;
import java.time.Instant;
import java.util.List;

/**
 * Port for producing deterministic, reproducible, explainable 0–100 technical scores.
 */
public interface TechnicalScoringService {

    String algorithmVersion();

    TechnicalScoreResult score(
            String symbol,
            List<OhlcvBar> orderedBars,
            TechnicalAnalysisResult indicatorResult,
            ScoreWeights weights,
            Instant asOf,
            long maxDataAgeHours,
            boolean rejectStaleData,
            boolean simulated);
}
