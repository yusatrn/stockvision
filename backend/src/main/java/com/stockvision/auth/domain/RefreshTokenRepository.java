package com.stockvision.auth.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for hashed opaque refresh tokens.
 */
public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    int revokeAllActiveByUserId(UUID userId, Instant revokedAt);
}
