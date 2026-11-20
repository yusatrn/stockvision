package com.stockvision.user.api;

import com.stockvision.user.domain.User;
import com.stockvision.user.domain.UserRole;
import com.stockvision.user.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Public user profile DTO. Never includes password hashes or internal credentials.
 */
public record UserProfileResponse(
        UUID id,
        String email,
        String displayName,
        UserRole role,
        UserStatus status,
        String timezone,
        Instant createdAt,
        Instant updatedAt) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.id(),
                user.email(),
                user.displayName(),
                user.role(),
                user.status(),
                user.timezone(),
                user.createdAt(),
                user.updatedAt());
    }
}
