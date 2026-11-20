package com.stockvision.alert.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for immutable alert trigger notifications.
 */
public interface AlertNotificationRepository {

    boolean existsByIdempotencyKey(String idempotencyKey);

    AlertNotification save(AlertNotification notification);

    NotificationPage findPageByUserId(UUID userId, boolean unreadOnly, int page, int size);

    Optional<AlertNotification> findByIdAndUserId(UUID id, UUID userId);

    int markAllReadByUserId(UUID userId, Instant readAt);
}
