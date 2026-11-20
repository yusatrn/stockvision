package com.stockvision.alert.domain;

import java.util.List;

/**
 * Paginated collection of user alert notifications accompanied by unread count.
 */
public record NotificationPage(
        List<AlertNotification> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        long unreadCount) {
}
