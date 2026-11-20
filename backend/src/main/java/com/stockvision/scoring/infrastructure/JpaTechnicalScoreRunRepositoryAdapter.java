package com.stockvision.scoring.infrastructure;

import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.scoring.domain.TechnicalScoreRun;
import com.stockvision.scoring.domain.TechnicalScoreRunRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Infrastructure adapter bridging {@link TechnicalScoreRunRepository} to Spring Data JPA.
 */
@Repository
public class JpaTechnicalScoreRunRepositoryAdapter implements TechnicalScoreRunRepository {

    private final SpringDataTechnicalScoreRunRepository springDataRepository;

    public JpaTechnicalScoreRunRepositoryAdapter(
            SpringDataTechnicalScoreRunRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public TechnicalScoreRun save(TechnicalScoreRun run) {
        return springDataRepository
                .saveAndFlush(TechnicalScoreRunJpaEntity.fromDomain(run))
                .toDomain();
    }

    @Override
    public Optional<TechnicalScoreRun> findById(UUID id) {
        return springDataRepository.findById(id).map(TechnicalScoreRunJpaEntity::toDomain);
    }

    @Override
    public Optional<TechnicalScoreRun> findLatestMatching(
            UUID instrumentId,
            BarInterval interval,
            String provider,
            boolean adjusted,
            String algorithmVersion) {
        return springDataRepository
                .findFirstByInstrumentIdAndBarIntervalAndProviderAndAdjustedAndAlgorithmVersionOrderByCalculatedAtDesc(
                        instrumentId,
                        interval.code(),
                        provider,
                        adjusted,
                        algorithmVersion)
                .map(TechnicalScoreRunJpaEntity::toDomain);
    }
}
