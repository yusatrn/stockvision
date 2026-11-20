package com.stockvision.ai.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link AiExplanationJpaEntity}.
 */
public interface SpringDataAiExplanationRepository
        extends JpaRepository<AiExplanationJpaEntity, UUID> {

    Optional<AiExplanationJpaEntity>
            findFirstByScoreRunIdAndPromptVersionAndRequestedProviderIdOrderByGeneratedAtDesc(
                    UUID scoreRunId, String promptVersion, String requestedProviderId);
}
