package com.stockvision.user.infrastructure;

import com.stockvision.common.domain.AuditableEntity;
import com.stockvision.user.domain.User;
import com.stockvision.user.domain.UserRole;
import com.stockvision.user.domain.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * JPA entity mapping the {@code users} table.
 */
@Entity
@Table(name = "users")
public class UserJpaEntity extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private UserStatus status;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    protected UserJpaEntity() {
    }

    public static UserJpaEntity fromDomain(User user) {
        UserJpaEntity entity = new UserJpaEntity();
        entity.id = user.id();
        entity.email = user.email();
        entity.displayName = user.displayName();
        entity.passwordHash = user.passwordHash();
        entity.role = user.role();
        entity.status = user.status();
        entity.timezone = user.timezone();
        return entity;
    }

    public User toDomain() {
        return new User(
                id,
                email,
                displayName,
                passwordHash,
                role,
                status,
                timezone,
                getCreatedAt(),
                getUpdatedAt());
    }
}
