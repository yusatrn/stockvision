package com.stockvision.marketdata.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for provider synchronization audit records.
 */
public interface ProviderSyncRunRepository {

    ProviderSyncRun save(ProviderSyncRun run);

    Optional<ProviderSyncRun> findLatestByInstrumentAndInterval(
            String provider, UUID instrumentId, BarInterval interval, boolean adjusted);

    Optional<ProviderSyncRun> findLatestByProvider(String provider);
}
