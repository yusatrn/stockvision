package com.stockvision.alert.infrastructure;

import com.stockvision.alert.domain.AlertNotification;
import com.stockvision.alert.domain.AlertNotificationRepository;
import com.stockvision.alert.domain.NotificationPage;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/**
 * Infrastructure adapter bridging {@link AlertNotificationRepository} to Spring Data JPA.
 */
@Repository
public class JpaAlertNotificationRepositoryAdapter implements AlertNotificationRepository {

    private final SpringDataAlertNotificationRepository repository;

    public JpaAlertNotificationRepositoryAdapter(
            SpringDataAlertNotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByIdempotencyKey(String idempotencyKey) {
        return repository.existsByIdempotencyKey(idempotencyKey);
    }

    @Override
    public AlertNotification save(AlertNotification notification) {
        if (notification.readAt() != null) {
            Optional<AlertNotificationJpaEntity> existing =
                    repository.findByIdAndUserId(notification.id(), notification.userId());
            if (existing.isPresent()) {
                AlertNotificationJpaEntity entity = existing.get();
                entity.markRead(notification.readAt());
                return repository.saveAndFlush(entity).toDomain();
            }
        }
        return repository
                .saveAndFlush(AlertNotificationJpaEntity.fromDomain(notification))
                .toDomain();
    }

    @Override
    public NotificationPage findPageByUserId(
            UUID userId, boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<AlertNotificationJpaEntity> result =
                unreadOnly
                        ? repository.findAllByUserIdAndReadAtIsNullOrderByCreatedAtDesc(
                                userId, pageable)
                        : repository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);

        long unreadCount = repository.countByUserIdAndReadAtIsNull(userId);
        return new NotificationPage(
                result.getContent().stream().map(AlertNotificationJpaEntity::toDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                unreadCount);
    }

    @Override
    public Optional<AlertNotification> findByIdAndUserId(UUID id, UUID userId) {
        return repository.findByIdAndUserId(id, userId).map(AlertNotificationJpaEntity::toDomain);
    }

    @Override
    public int markAllReadByUserId(UUID userId, Instant readAt) {
        return repository.markAllReadByUserId(userId, readAt);
    }
}
