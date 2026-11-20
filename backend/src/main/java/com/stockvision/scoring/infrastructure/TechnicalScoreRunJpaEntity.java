package com.stockvision.scoring.infrastructure;

import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.scoring.domain.ScoreRegime;
import com.stockvision.scoring.domain.ScoreStatus;
import com.stockvision.scoring.domain.TechnicalScoreRun;
import com.stockvision.scoring.domain.TrendDirection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code technical_score_runs} table.
 */
@Entity
@Table(name = "technical_score_runs")
public class TechnicalScoreRunJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "analysis_run_id", nullable = false)
    private UUID analysisRunId;

    @Column(name = "instrument_id", nullable = false)
    private UUID instrumentId;

    @Column(name = "symbol", nullable = false, length = 24)
    private String symbol;

    @Column(name = "exchange_code", nullable = false, length = 16)
    private String exchangeCode;

    @Column(name = "bar_interval", nullable = false, length = 16)
    private String barInterval;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    @Column(name = "adjusted", nullable = false)
    private boolean adjusted;

    @Column(name = "simulated", nullable = false)
    private boolean simulated;

    @Column(name = "algorithm_version", nullable = false, length = 64)
    private String algorithmVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ScoreStatus status;

    @Column(name = "stale_data", nullable = false)
    private boolean staleData;

    @Column(name = "final_score", precision = 6, scale = 2)
    private BigDecimal finalScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "score_regime", nullable = false, length = 40)
    private ScoreRegime scoreRegime;

    @Enumerated(EnumType.STRING)
    @Column(name = "trend_direction", nullable = false, length = 24)
    private TrendDirection trendDirection;

    @Column(name = "latest_close", precision = 19, scale = 4)
    private BigDecimal latestClose;

    @Column(name = "latest_rsi", precision = 10, scale = 6)
    private BigDecimal latestRsi;

    @Column(name = "weights_json", nullable = false, columnDefinition = "TEXT")
    private String weightsJson;

    @Column(name = "components_json", nullable = false, columnDefinition = "TEXT")
    private String componentsJson;

    @Column(name = "explanation_json", nullable = false, columnDefinition = "TEXT")
    private String explanationJson;

    @Column(name = "range_from")
    private Instant rangeFrom;

    @Column(name = "range_to")
    private Instant rangeTo;

    @Column(name = "latest_bar_timestamp")
    private Instant latestBarTimestamp;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    protected TechnicalScoreRunJpaEntity() {
    }

    public static TechnicalScoreRunJpaEntity fromDomain(TechnicalScoreRun run) {
        TechnicalScoreRunJpaEntity entity = new TechnicalScoreRunJpaEntity();
        entity.id = run.id();
        entity.analysisRunId = run.analysisRunId();
        entity.instrumentId = run.instrumentId();
        entity.symbol = run.symbol();
        entity.exchangeCode = run.exchangeCode();
        entity.barInterval = run.interval().code();
        entity.provider = run.provider();
        entity.adjusted = run.adjusted();
        entity.simulated = run.simulated();
        entity.algorithmVersion = run.algorithmVersion();
        entity.status = run.status();
        entity.staleData = run.staleData();
        entity.finalScore = run.finalScore();
        entity.scoreRegime = run.regime();
        entity.trendDirection = run.trendDirection();
        entity.latestClose = run.latestClose();
        entity.latestRsi = run.latestRsi();
        entity.weightsJson = run.weightsJson();
        entity.componentsJson = run.componentsJson();
        entity.explanationJson = run.explanationJson();
        entity.rangeFrom = run.rangeFrom();
        entity.rangeTo = run.rangeTo();
        entity.latestBarTimestamp = run.latestBarTimestamp();
        entity.calculatedAt = run.calculatedAt();
        return entity;
    }

    public TechnicalScoreRun toDomain() {
        return new TechnicalScoreRun(
                id,
                analysisRunId,
                instrumentId,
                symbol,
                exchangeCode,
                BarInterval.fromCode(barInterval),
                provider,
                adjusted,
                simulated,
                algorithmVersion,
                status,
                staleData,
                finalScore,
                scoreRegime,
                trendDirection,
                latestClose,
                latestRsi,
                weightsJson,
                componentsJson,
                explanationJson,
                rangeFrom,
                rangeTo,
                latestBarTimestamp,
                calculatedAt);
    }
}
