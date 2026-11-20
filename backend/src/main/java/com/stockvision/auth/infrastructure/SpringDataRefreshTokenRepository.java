package com.stockvision.auth.infrastructure;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link RefreshTokenJpaEntity}.
 */
public interface SpringDataRefreshTokenRepository
        extends JpaRepository<RefreshTokenJpaEntity, UUID> {

    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            UPDATE RefreshTokenJpaEntity t
            SET t.revokedAt = :revokedAt
            WHERE t.userId = :userId
              AND t.revokedAt IS NULL
            """)
    int revokeAllActiveByUserId(
            @Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);
}
