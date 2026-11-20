package com.stockvision.alert.infrastructure;

import com.stockvision.alert.domain.AlertStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link AlertRuleJpaEntity}.
 */
public interface SpringDataAlertRuleRepository extends JpaRepository<AlertRuleJpaEntity, UUID> {

    List<AlertRuleJpaEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    List<AlertRuleJpaEntity> findAllByStatusOrderByCreatedAtAsc(AlertStatus status);

    List<AlertRuleJpaEntity> findAllByUserIdAndStatusOrderByCreatedAtAsc(
            UUID userId, AlertStatus status);

    Optional<AlertRuleJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    void deleteByIdAndUserId(UUID id, UUID userId);
}
