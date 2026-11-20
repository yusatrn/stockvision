package com.stockvision.technicalanalysis.infrastructure;

import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.technicalanalysis.domain.AnalysisRun;
import com.stockvision.technicalanalysis.domain.AnalysisRunRepository;
import com.stockvision.technicalanalysis.domain.AnalysisRunType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Infrastructure adapter bridging {@link AnalysisRunRepository} to Spring Data JPA.
 */
@Repository
public class JpaAnalysisRunRepositoryAdapter implements AnalysisRunRepository {

    private final SpringDataAnalysisRunRepository springDataRepository;

    public JpaAnalysisRunRepositoryAdapter(SpringDataAnalysisRunRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public AnalysisRun save(AnalysisRun run) {
        return springDataRepository.saveAndFlush(AnalysisRunJpaEntity.fromDomain(run)).toDomain();
    }

    @Override
    public Optional<AnalysisRun> findLatestByInstrumentAndInterval(
            UUID instrumentId, BarInterval interval, AnalysisRunType runType) {
        return springDataRepository
                .findFirstByInstrumentIdAndBarIntervalAndRunTypeOrderByCalculatedAtDesc(
                        instrumentId, interval.code(), runType)
                .map(AnalysisRunJpaEntity::toDomain);
    }
}
