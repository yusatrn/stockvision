package com.stockvision.marketdata.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link ProviderSyncRunJpaEntity}.
 */
public interface SpringDataProviderSyncRunRepository
        extends JpaRepository<ProviderSyncRunJpaEntity, UUID> {

    Optional<ProviderSyncRunJpaEntity>
            findFirstByProviderAndInstrumentIdAndBarIntervalAndAdjustedOrderByCompletedAtDesc(
                    String provider, UUID instrumentId, String barInterval, boolean adjusted);

    Optional<ProviderSyncRunJpaEntity> findFirstByProviderOrderByCompletedAtDesc(String provider);
}
