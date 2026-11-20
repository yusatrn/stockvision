package com.stockvision.marketdata.application;

import com.stockvision.marketdata.domain.ProviderSyncRun;
import com.stockvision.marketdata.domain.ProviderSyncRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists {@link ProviderSyncRun} audit records in an isolated {@code REQUIRES_NEW} transaction
 * so failure records survive caller rollbacks.
 */
@Service
public class SyncRunAuditService {

    private final ProviderSyncRunRepository providerSyncRunRepository;

    public SyncRunAuditService(ProviderSyncRunRepository providerSyncRunRepository) {
        this.providerSyncRunRepository = providerSyncRunRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProviderSyncRun record(ProviderSyncRun run) {
        return providerSyncRunRepository.save(run);
    }
}
