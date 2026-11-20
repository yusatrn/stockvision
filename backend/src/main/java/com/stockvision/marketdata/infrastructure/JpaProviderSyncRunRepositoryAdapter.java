package com.stockvision.marketdata.infrastructure;

import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.marketdata.domain.ProviderSyncRun;
import com.stockvision.marketdata.domain.ProviderSyncRunRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Infrastructure adapter bridging {@link ProviderSyncRunRepository} to Spring Data JPA.
 */
@Repository
public class JpaProviderSyncRunRepositoryAdapter implements ProviderSyncRunRepository {

    private final SpringDataProviderSyncRunRepository springDataRepository;

    public JpaProviderSyncRunRepositoryAdapter(
            SpringDataProviderSyncRunRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public ProviderSyncRun save(ProviderSyncRun run) {
        return springDataRepository
                .saveAndFlush(ProviderSyncRunJpaEntity.fromDomain(run))
                .toDomain();
    }

    @Override
    public Optional<ProviderSyncRun> findLatestByInstrumentAndInterval(
            String provider, UUID instrumentId, BarInterval interval, boolean adjusted) {
        return springDataRepository
                .findFirstByProviderAndInstrumentIdAndBarIntervalAndAdjustedOrderByCompletedAtDesc(
                        provider, instrumentId, interval.code(), adjusted)
                .map(ProviderSyncRunJpaEntity::toDomain);
    }

    @Override
    public Optional<ProviderSyncRun> findLatestByProvider(String provider) {
        return springDataRepository
                .findFirstByProviderOrderByCompletedAtDesc(provider)
                .map(ProviderSyncRunJpaEntity::toDomain);
    }
}
