package com.stockvision.auth.domain;

import com.stockvision.user.domain.UserRole;
import java.time.Instant;
import java.util.UUID;

/**
 * Validated claims extracted from a signed JWT access token.
 */
public record JwtTokenClaims(
        UUID userId,
        UserRole role,
        Instant issuedAt,
        Instant expiresAt,
        String tokenId) {
}
