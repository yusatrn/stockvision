package com.stockvision.alert.infrastructure;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link AlertNotificationJpaEntity}.
 */
public interface SpringDataAlertNotificationRepository
        extends JpaRepository<AlertNotificationJpaEntity, UUID> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<AlertNotificationJpaEntity> findAllByUserIdOrderByCreatedAtDesc(
            UUID userId, Pageable pageable);

    Page<AlertNotificationJpaEntity> findAllByUserIdAndReadAtIsNullOrderByCreatedAtDesc(
            UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    Optional<AlertNotificationJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            UPDATE AlertNotificationJpaEntity n
            SET n.readAt = :readAt
            WHERE n.userId = :userId
              AND n.readAt IS NULL
            """)
    int markAllReadByUserId(@Param("userId") UUID userId, @Param("readAt") Instant readAt);
}
