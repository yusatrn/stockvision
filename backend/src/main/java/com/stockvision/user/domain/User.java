package com.stockvision.user.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Framework-independent user domain model.
 */
public record User(
        UUID id,
        String email,
        String displayName,
        String passwordHash,
        UserRole role,
        UserStatus status,
        String timezone,
        Instant createdAt,
        Instant updatedAt) {

    public User {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(timezone, "timezone must not be null");
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /**
     * Excludes {@code passwordHash} and PII fields such as {@code email} to avoid accidental leaks
     * in logs or error diagnostics.
     */
    @Override
    public String toString() {
        return "User[id=" + id + ", role=" + role + ", status=" + status + "]";
    }
}
