package com.stockvision.technicalanalysis.infrastructure;

import com.stockvision.technicalanalysis.domain.AnalysisRunType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link AnalysisRunJpaEntity}.
 */
public interface SpringDataAnalysisRunRepository extends JpaRepository<AnalysisRunJpaEntity, UUID> {

    Optional<AnalysisRunJpaEntity>
            findFirstByInstrumentIdAndBarIntervalAndRunTypeOrderByCalculatedAtDesc(
                    UUID instrumentId, String barInterval, AnalysisRunType runType);
}
