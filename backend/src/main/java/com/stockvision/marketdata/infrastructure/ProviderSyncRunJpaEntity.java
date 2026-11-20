package com.stockvision.marketdata.infrastructure;

import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.marketdata.domain.ProviderSyncRun;
import com.stockvision.marketdata.domain.SyncRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code provider_sync_runs} audit table.
 */
@Entity
@Table(name = "provider_sync_runs")
public class ProviderSyncRunJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    @Column(name = "instrument_id")
    private UUID instrumentId;

    @Column(name = "symbol", length = 24)
    private String symbol;

    @Column(name = "bar_interval", length = 16)
    private String barInterval;

    @Column(name = "adjusted", nullable = false)
    private boolean adjusted;

    @Column(name = "simulated", nullable = false)
    private boolean simulated;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private SyncRunStatus status;

    @Column(name = "bars_fetched", nullable = false)
    private int barsFetched;

    @Column(name = "bars_inserted", nullable = false)
    private int barsInserted;

    @Column(name = "bars_skipped", nullable = false)
    private int barsSkipped;

    @Column(name = "range_from")
    private Instant rangeFrom;

    @Column(name = "range_to")
    private Instant rangeTo;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    protected ProviderSyncRunJpaEntity() {
    }

    public static ProviderSyncRunJpaEntity fromDomain(ProviderSyncRun run) {
        ProviderSyncRunJpaEntity entity = new ProviderSyncRunJpaEntity();
        entity.id = run.id();
        entity.provider = run.provider();
        entity.instrumentId = run.instrumentId();
        entity.symbol = run.symbol();
        entity.barInterval = run.interval() != null ? run.interval().code() : null;
        entity.adjusted = run.adjusted();
        entity.simulated = run.simulated();
        entity.status = run.status();
        entity.barsFetched = run.barsFetched();
        entity.barsInserted = run.barsInserted();
        entity.barsSkipped = run.barsSkipped();
        entity.rangeFrom = run.rangeFrom();
        entity.rangeTo = run.rangeTo();
        entity.startedAt = run.startedAt();
        entity.completedAt = run.completedAt();
        entity.errorMessage = run.errorMessage();
        return entity;
    }

    public ProviderSyncRun toDomain() {
        return new ProviderSyncRun(
                id,
                provider,
                instrumentId,
                symbol,
                barInterval != null ? BarInterval.fromCode(barInterval) : null,
                adjusted,
                simulated,
                status,
                barsFetched,
                barsInserted,
                barsSkipped,
                rangeFrom,
                rangeTo,
                startedAt,
                completedAt,
                errorMessage);
    }
}
