package com.stockvision.user.infrastructure;

import com.stockvision.user.domain.User;
import com.stockvision.user.domain.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Infrastructure adapter bridging {@link UserRepository} to Spring Data JPA.
 */
@Repository
public class JpaUserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository springDataUserRepository;

    public JpaUserRepositoryAdapter(SpringDataUserRepository springDataUserRepository) {
        this.springDataUserRepository = springDataUserRepository;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return springDataUserRepository.findById(id).map(UserJpaEntity::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String normalizedEmail) {
        return springDataUserRepository.findByEmail(normalizedEmail).map(UserJpaEntity::toDomain);
    }

    @Override
    public boolean existsByEmail(String normalizedEmail) {
        return springDataUserRepository.existsByEmail(normalizedEmail);
    }

    @Override
    public User save(User user) {
        UserJpaEntity saved = springDataUserRepository.saveAndFlush(UserJpaEntity.fromDomain(user));
        return saved.toDomain();
    }
}
