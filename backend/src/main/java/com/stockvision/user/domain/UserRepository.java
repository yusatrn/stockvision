package com.stockvision.user.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for user accounts.
 */
public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String normalizedEmail);

    boolean existsByEmail(String normalizedEmail);

    User save(User user);
}
