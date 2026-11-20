package com.stockvision.auth.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Hashed refresh token domain model supporting rotation and replay revocation.
 */
public record RefreshToken(
        UUID id,
        UUID userId,
        String tokenHash,
        Instant expiresAt,
        Instant revokedAt,
        UUID replacedById,
        Instant createdAt) {

    public RefreshToken {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public RefreshToken revoke(Instant when, UUID replacementId) {
        return new RefreshToken(id, userId, tokenHash, expiresAt, when, replacementId, createdAt);
    }
}
