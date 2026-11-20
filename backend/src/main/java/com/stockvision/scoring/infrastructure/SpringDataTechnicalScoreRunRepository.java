package com.stockvision.scoring.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link TechnicalScoreRunJpaEntity}.
 */
public interface SpringDataTechnicalScoreRunRepository
        extends JpaRepository<TechnicalScoreRunJpaEntity, UUID> {

    Optional<TechnicalScoreRunJpaEntity>
            findFirstByInstrumentIdAndBarIntervalAndProviderAndAdjustedAndAlgorithmVersionOrderByCalculatedAtDesc(
                    UUID instrumentId,
                    String barInterval,
                    String provider,
                    boolean adjusted,
                    String algorithmVersion);
}
