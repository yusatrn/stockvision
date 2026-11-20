package com.stockvision.ai.domain;

import com.stockvision.marketdata.domain.BarInterval;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persisted AI or template explanation bound to exact analysis and score runs for auditability.
 */
public record AiExplanation(
        UUID id,
        UUID analysisRunId,
        UUID scoreRunId,
        UUID instrumentId,
        String symbol,
        BarInterval barInterval,
        String providerId,
        String requestedProviderId,
        String modelName,
        String promptVersion,
        AiExplanationStatus status,
        boolean fallbackUsed,
        AiFallbackReason fallbackReason,
        boolean simulatedData,
        boolean staleData,
        String structuredOutputJson,
        String disclaimer,
        Instant generatedAt) {

    public AiExplanation {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(analysisRunId, "analysisRunId must not be null");
        Objects.requireNonNull(scoreRunId, "scoreRunId must not be null");
        Objects.requireNonNull(instrumentId, "instrumentId must not be null");
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(barInterval, "barInterval must not be null");
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(requestedProviderId, "requestedProviderId must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
        Objects.requireNonNull(promptVersion, "promptVersion must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(structuredOutputJson, "structuredOutputJson must not be null");
        Objects.requireNonNull(disclaimer, "disclaimer must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
    }
}
