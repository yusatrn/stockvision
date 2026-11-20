package com.stockvision.ai.infrastructure;

import com.stockvision.ai.domain.AiExplanation;
import com.stockvision.ai.domain.AiExplanationStatus;
import com.stockvision.ai.domain.AiFallbackReason;
import com.stockvision.marketdata.domain.BarInterval;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code ai_explanations} audit table.
 */
@Entity
@Table(name = "ai_explanations")
public class AiExplanationJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "analysis_run_id", nullable = false, updatable = false)
    private UUID analysisRunId;

    @Column(name = "score_run_id", nullable = false, updatable = false)
    private UUID scoreRunId;

    @Column(name = "instrument_id", nullable = false, updatable = false)
    private UUID instrumentId;

    @Column(name = "symbol", nullable = false, updatable = false, length = 24)
    private String symbol;

    @Column(name = "bar_interval", nullable = false, updatable = false, length = 16)
    private String barInterval;

    @Column(name = "provider_id", nullable = false, updatable = false, length = 64)
    private String providerId;

    @Column(name = "requested_provider_id", nullable = false, updatable = false, length = 64)
    private String requestedProviderId;

    @Column(name = "model_name", nullable = false, updatable = false, length = 120)
    private String modelName;

    @Column(name = "prompt_version", nullable = false, updatable = false, length = 64)
    private String promptVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 32)
    private AiExplanationStatus status;

    @Column(name = "fallback_used", nullable = false, updatable = false)
    private boolean fallbackUsed;

    @Enumerated(EnumType.STRING)
    @Column(name = "fallback_reason", updatable = false, length = 64)
    private AiFallbackReason fallbackReason;

    @Column(name = "simulated_data", nullable = false, updatable = false)
    private boolean simulatedData;

    @Column(name = "stale_data", nullable = false, updatable = false)
    private boolean staleData;

    @Column(name = "structured_output_json", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String structuredOutputJson;

    @Column(name = "disclaimer", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String disclaimer;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    protected AiExplanationJpaEntity() {
    }

    public static AiExplanationJpaEntity fromDomain(AiExplanation domain) {
        AiExplanationJpaEntity entity = new AiExplanationJpaEntity();
        entity.id = domain.id();
        entity.analysisRunId = domain.analysisRunId();
        entity.scoreRunId = domain.scoreRunId();
        entity.instrumentId = domain.instrumentId();
        entity.symbol = domain.symbol();
        entity.barInterval = domain.barInterval().code();
        entity.providerId = domain.providerId();
        entity.requestedProviderId = domain.requestedProviderId();
        entity.modelName = domain.modelName();
        entity.promptVersion = domain.promptVersion();
        entity.status = domain.status();
        entity.fallbackUsed = domain.fallbackUsed();
        entity.fallbackReason = domain.fallbackReason();
        entity.simulatedData = domain.simulatedData();
        entity.staleData = domain.staleData();
        entity.structuredOutputJson = domain.structuredOutputJson();
        entity.disclaimer = domain.disclaimer();
        entity.generatedAt = domain.generatedAt();
        return entity;
    }

    public AiExplanation toDomain() {
        return new AiExplanation(
                id,
                analysisRunId,
                scoreRunId,
                instrumentId,
                symbol,
                BarInterval.fromCode(barInterval),
                providerId,
                requestedProviderId,
                modelName,
                promptVersion,
                status,
                fallbackUsed,
                fallbackReason,
                simulatedData,
                staleData,
                structuredOutputJson,
                disclaimer,
                generatedAt);
    }
}
