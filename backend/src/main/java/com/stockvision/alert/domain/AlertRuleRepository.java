package com.stockvision.alert.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for user-owned alert threshold rules.
 */
public interface AlertRuleRepository {

    List<AlertRule> findAllByUserId(UUID userId);

    List<AlertRule> findAllActive();

    List<AlertRule> findAllActiveByUserId(UUID userId);

    Optional<AlertRule> findByIdAndUserId(UUID id, UUID userId);

    AlertRule save(AlertRule rule);

    void deleteByIdAndUserId(UUID id, UUID userId);
}
