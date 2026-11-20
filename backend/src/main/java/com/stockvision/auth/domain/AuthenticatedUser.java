package com.stockvision.auth.domain;

import com.stockvision.user.domain.UserRole;
import java.util.UUID;

/**
 * Security principal placed in the Spring Security context after validating an access token.
 */
public record AuthenticatedUser(
        UUID userId,
        String email,
        UserRole role) {
}
