package com.stockvision.auth.infrastructure;

import com.stockvision.auth.domain.RefreshToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code refresh_tokens} table.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshTokenJpaEntity() {
    }

    public static RefreshTokenJpaEntity fromDomain(RefreshToken token) {
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity();
        entity.id = token.id();
        entity.userId = token.userId();
        entity.tokenHash = token.tokenHash();
        entity.expiresAt = token.expiresAt();
        entity.revokedAt = token.revokedAt();
        entity.replacedById = token.replacedById();
        entity.createdAt = token.createdAt() != null ? token.createdAt() : Instant.now();
        return entity;
    }

    public void applyRevocation(Instant revokedAt, UUID replacedById) {
        if (this.revokedAt == null) {
            this.revokedAt = revokedAt;
            this.replacedById = replacedById;
        }
    }

    public RefreshToken toDomain() {
        return new RefreshToken(
                id, userId, tokenHash, expiresAt, revokedAt, replacedById, createdAt);
    }
}
