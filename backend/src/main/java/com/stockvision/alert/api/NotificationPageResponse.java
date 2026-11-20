package com.stockvision.alert.api;

import java.util.List;

/**
 * Paginated user alert notification feed.
 */
public record NotificationPageResponse(
        List<AlertNotificationResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        long unreadCount) {
}
