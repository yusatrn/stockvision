package com.stockvision.technicalanalysis.domain;

import com.stockvision.marketdata.domain.BarInterval;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for analysis run metadata and versioning audit records.
 */
public interface AnalysisRunRepository {

    AnalysisRun save(AnalysisRun run);

    Optional<AnalysisRun> findLatestByInstrumentAndInterval(
            UUID instrumentId, BarInterval interval, AnalysisRunType runType);
}
