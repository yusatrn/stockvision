package com.stockvision.ai.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for auditable natural-language explanations.
 */
public interface AiExplanationRepository {

    AiExplanation save(AiExplanation explanation);

    Optional<AiExplanation> findLatestByScoreRunAndPromptAndRequestedProvider(
            UUID scoreRunId, String promptVersion, String requestedProviderId);

    Optional<AiExplanation> findById(UUID id);
}
