package com.stockvision.auth.api;

import com.stockvision.user.api.UserProfileResponse;
import java.time.Instant;

/**
 * Authentication response returning a Bearer JWT access token, user profile, and opaque
 * rotatable refresh token.
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        Instant expiresAt,
        UserProfileResponse user,
        String refreshToken,
        Instant refreshExpiresAt) {
}
