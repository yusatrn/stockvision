package com.stockvision.portfolio.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link PortfolioJpaEntity}.
 */
public interface SpringDataPortfolioRepository extends JpaRepository<PortfolioJpaEntity, UUID> {

    List<PortfolioJpaEntity> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<PortfolioJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

    void deleteByIdAndUserId(UUID id, UUID userId);
}
