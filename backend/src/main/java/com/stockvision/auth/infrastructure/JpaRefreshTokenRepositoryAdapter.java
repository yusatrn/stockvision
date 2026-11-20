package com.stockvision.auth.infrastructure;

import com.stockvision.auth.domain.RefreshToken;
import com.stockvision.auth.domain.RefreshTokenRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Infrastructure adapter bridging {@link RefreshTokenRepository} to Spring Data JPA.
 */
@Repository
public class JpaRefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final SpringDataRefreshTokenRepository repository;

    public JpaRefreshTokenRepositoryAdapter(SpringDataRefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        RefreshTokenJpaEntity entity =
                repository
                        .findById(token.id())
                        .map(
                                existing -> {
                                    if (token.isRevoked()) {
                                        existing.applyRevocation(
                                                token.revokedAt(), token.replacedById());
                                    }
                                    return existing;
                                })
                        .orElseGet(() -> RefreshTokenJpaEntity.fromDomain(token));
        return repository.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(RefreshTokenJpaEntity::toDomain);
    }

    @Override
    public int revokeAllActiveByUserId(UUID userId, Instant revokedAt) {
        return repository.revokeAllActiveByUserId(userId, revokedAt);
    }
}
