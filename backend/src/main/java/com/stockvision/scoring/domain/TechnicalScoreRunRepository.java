package com.stockvision.scoring.domain;

import com.stockvision.marketdata.domain.BarInterval;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for explainable technical score runs.
 */
public interface TechnicalScoreRunRepository {

    TechnicalScoreRun save(TechnicalScoreRun run);

    Optional<TechnicalScoreRun> findById(UUID id);

    Optional<TechnicalScoreRun> findLatestMatching(
            UUID instrumentId,
            BarInterval interval,
            String provider,
            boolean adjusted,
            String algorithmVersion);
}
