package com.stockvision.ai.infrastructure;

import com.stockvision.ai.domain.AiExplanation;
import com.stockvision.ai.domain.AiExplanationRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Infrastructure adapter bridging {@link AiExplanationRepository} to Spring Data JPA.
 */
@Repository
public class JpaAiExplanationRepositoryAdapter implements AiExplanationRepository {

    private final SpringDataAiExplanationRepository repository;

    public JpaAiExplanationRepositoryAdapter(SpringDataAiExplanationRepository repository) {
        this.repository = repository;
    }

    @Override
    public AiExplanation save(AiExplanation explanation) {
        return repository.saveAndFlush(AiExplanationJpaEntity.fromDomain(explanation)).toDomain();
    }

    @Override
    public Optional<AiExplanation> findLatestByScoreRunAndPromptAndRequestedProvider(
            UUID scoreRunId, String promptVersion, String requestedProviderId) {
        return repository
                .findFirstByScoreRunIdAndPromptVersionAndRequestedProviderIdOrderByGeneratedAtDesc(
                        scoreRunId, promptVersion, requestedProviderId)
                .map(AiExplanationJpaEntity::toDomain);
    }

    @Override
    public Optional<AiExplanation> findById(UUID id) {
        return repository.findById(id).map(AiExplanationJpaEntity::toDomain);
    }
}
