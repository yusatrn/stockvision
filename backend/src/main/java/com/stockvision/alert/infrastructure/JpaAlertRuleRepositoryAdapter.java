package com.stockvision.alert.infrastructure;

import com.stockvision.alert.domain.AlertRule;
import com.stockvision.alert.domain.AlertRuleRepository;
import com.stockvision.alert.domain.AlertStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Infrastructure adapter bridging {@link AlertRuleRepository} to Spring Data JPA.
 */
@Repository
public class JpaAlertRuleRepositoryAdapter implements AlertRuleRepository {

    private final SpringDataAlertRuleRepository repository;

    public JpaAlertRuleRepositoryAdapter(SpringDataAlertRuleRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<AlertRule> findAllByUserId(UUID userId) {
        return repository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(AlertRuleJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<AlertRule> findAllActive() {
        return repository.findAllByStatusOrderByCreatedAtAsc(AlertStatus.ACTIVE).stream()
                .map(AlertRuleJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<AlertRule> findAllActiveByUserId(UUID userId) {
        return repository
                .findAllByUserIdAndStatusOrderByCreatedAtAsc(userId, AlertStatus.ACTIVE)
                .stream()
                .map(AlertRuleJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<AlertRule> findByIdAndUserId(UUID id, UUID userId) {
        return repository.findByIdAndUserId(id, userId).map(AlertRuleJpaEntity::toDomain);
    }

    @Override
    public AlertRule save(AlertRule rule) {
        AlertRuleJpaEntity entity =
                repository
                        .findByIdAndUserId(rule.id(), rule.userId())
                        .map(
                                existing -> {
                                    existing.applyChanges(rule);
                                    return existing;
                                })
                        .orElseGet(() -> AlertRuleJpaEntity.fromDomain(rule));
        return repository.saveAndFlush(entity).toDomain();
    }

    @Override
    public void deleteByIdAndUserId(UUID id, UUID userId) {
        repository.deleteByIdAndUserId(id, userId);
    }
}
