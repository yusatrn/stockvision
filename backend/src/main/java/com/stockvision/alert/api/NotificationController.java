package com.stockvision.alert.api;

import com.stockvision.alert.application.NotificationService;
import com.stockvision.auth.domain.AuthenticatedUser;
import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated endpoints for viewing and acknowledging triggered alert notifications.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Alert notifications with freshness and provider attribution")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "List alert notifications", description = "Paginates triggered alert notifications owned by the authenticated user.")
    public NotificationPageResponse listNotifications(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "unreadOnly", defaultValue = "false") boolean unreadOnly,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return notificationService.listNotifications(requireUserId(principal), unreadOnly, page, size);
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark notification read", description = "Marks a single owned notification as read in UTC.")
    public AlertNotificationResponse markRead(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable("id") UUID id) {
        return notificationService.markRead(requireUserId(principal), id);
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all notifications read", description = "Marks all unread notifications owned by the authenticated user as read.")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal AuthenticatedUser principal) {
        int updated = notificationService.markAllRead(requireUserId(principal));
        return Map.of("updatedCount", updated);
    }

    private static UUID requireUserId(AuthenticatedUser principal) {
        if (principal == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        return principal.userId();
    }
}
