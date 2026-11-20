package com.stockvision.ai.api;

import com.stockvision.ai.domain.AiExplanationStatus;
import com.stockvision.ai.domain.AiFallbackReason;
import com.stockvision.ai.domain.StructuredAiExplanation;
import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import com.stockvision.marketdata.api.FreshnessMetadataResponse;
import java.time.Instant;
import java.util.UUID;

/**
 * Auditable AI or deterministic template explanation bound to persisted quantitative runs.
 */
public record AiExplanationResponse(
        UUID explanationId,
        UUID analysisRunId,
        UUID scoreRunId,
        String symbol,
        String interval,
        boolean adjusted,
        String providerId,
        String requestedProviderId,
        String modelName,
        String promptVersion,
        AiExplanationStatus status,
        boolean fallbackUsed,
        AiFallbackReason fallbackReason,
        boolean simulatedData,
        boolean staleData,
        Instant generatedAt,
        StructuredAiExplanation explanation,
        DataSourceMetadataResponse dataSource,
        FreshnessMetadataResponse freshness,
        String disclaimer) {
}
