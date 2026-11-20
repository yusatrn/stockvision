package com.stockvision.technicalanalysis.infrastructure;

import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.technicalanalysis.domain.AnalysisRun;
import com.stockvision.technicalanalysis.domain.AnalysisRunStatus;
import com.stockvision.technicalanalysis.domain.AnalysisRunType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code analysis_runs} table.
 */
@Entity
@Table(name = "analysis_runs")
public class AnalysisRunJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "instrument_id", nullable = false)
    private UUID instrumentId;

    @Column(name = "symbol", nullable = false, length = 24)
    private String symbol;

    @Column(name = "bar_interval", nullable = false, length = 16)
    private String barInterval;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    @Column(name = "adjusted", nullable = false)
    private boolean adjusted;

    @Column(name = "simulated", nullable = false)
    private boolean simulated;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 32)
    private AnalysisRunType runType;

    @Column(name = "engine_version", nullable = false, length = 64)
    private String engineVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AnalysisRunStatus status;

    @Column(name = "parameters_json", nullable = false, columnDefinition = "TEXT")
    private String parametersJson;

    @Column(name = "bar_count", nullable = false)
    private int barCount;

    @Column(name = "range_from")
    private Instant rangeFrom;

    @Column(name = "range_to")
    private Instant rangeTo;

    @Column(name = "latest_bar_timestamp")
    private Instant latestBarTimestamp;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    protected AnalysisRunJpaEntity() {
    }

    public static AnalysisRunJpaEntity fromDomain(AnalysisRun run) {
        AnalysisRunJpaEntity entity = new AnalysisRunJpaEntity();
        entity.id = run.id();
        entity.instrumentId = run.instrumentId();
        entity.symbol = run.symbol();
        entity.barInterval = run.interval().code();
        entity.provider = run.provider();
        entity.adjusted = run.adjusted();
        entity.simulated = run.simulated();
        entity.runType = run.runType();
        entity.engineVersion = run.engineVersion();
        entity.status = run.status();
        entity.parametersJson = run.parametersJson();
        entity.barCount = run.barCount();
        entity.rangeFrom = run.rangeFrom();
        entity.rangeTo = run.rangeTo();
        entity.latestBarTimestamp = run.latestBarTimestamp();
        entity.calculatedAt = run.calculatedAt();
        return entity;
    }

    public AnalysisRun toDomain() {
        return new AnalysisRun(
                id,
                instrumentId,
                symbol,
                BarInterval.fromCode(barInterval),
                provider,
                adjusted,
                simulated,
                runType,
                engineVersion,
                status,
                parametersJson,
                barCount,
                rangeFrom,
                rangeTo,
                latestBarTimestamp,
                calculatedAt);
    }
}
