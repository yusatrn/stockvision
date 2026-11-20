package com.stockvision.alert.application;

import com.stockvision.alert.api.AlertNotificationResponse;
import com.stockvision.alert.api.NotificationPageResponse;
import com.stockvision.alert.domain.AlertNotification;
import com.stockvision.alert.domain.AlertNotificationRepository;
import com.stockvision.alert.domain.NotificationPage;
import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service serving user-scoped alert notification feeds and read-state transitions.
 */
@Service
public class NotificationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AlertNotificationRepository notificationRepository;
    private final Clock clock;

    public NotificationService(AlertNotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse listNotifications(
            UUID userId, boolean unreadOnly, int page, int size) {
        if (page < 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "page must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR, "size must be between 1 and " + MAX_PAGE_SIZE);
        }

        NotificationPage result =
                notificationRepository.findPageByUserId(userId, unreadOnly, page, size);

        return new NotificationPageResponse(
                result.items().stream().map(AlertNotificationResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.unreadCount());
    }

    @Transactional
    public AlertNotificationResponse markRead(UUID userId, UUID notificationId) {
        AlertNotification existing =
                notificationRepository
                        .findByIdAndUserId(notificationId, userId)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.NOT_FOUND, "Notification not found"));

        if (existing.readAt() != null) {
            return AlertNotificationResponse.from(existing);
        }

        Instant now = clock.instant();
        AlertNotification updated =
                new AlertNotification(
                        existing.id(),
                        existing.alertRuleId(),
                        existing.userId(),
                        existing.instrumentId(),
                        existing.symbol(),
                        existing.conditionType(),
                        existing.indicatorType(),
                        existing.indicatorField(),
                        existing.thresholdValue(),
                        existing.observedValue(),
                        existing.dataTimestamp(),
                        existing.providerId(),
                        existing.providerDisplayName(),
                        existing.providerMode(),
                        existing.simulated(),
                        existing.freshnessStatus(),
                        existing.staleData(),
                        existing.message(),
                        existing.idempotencyKey(),
                        now,
                        existing.createdAt());

        return AlertNotificationResponse.from(notificationRepository.save(updated));
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllReadByUserId(userId, clock.instant());
    }
}
