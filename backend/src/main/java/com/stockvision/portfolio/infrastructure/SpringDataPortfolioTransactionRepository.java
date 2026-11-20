package com.stockvision.portfolio.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link PortfolioTransactionJpaEntity}.
 */
public interface SpringDataPortfolioTransactionRepository
        extends JpaRepository<PortfolioTransactionJpaEntity, UUID> {

    List<PortfolioTransactionJpaEntity> findAllByPortfolioIdOrderByExecutedAtAscCreatedAtAscIdAsc(
            UUID portfolioId);

    List<PortfolioTransactionJpaEntity> findAllByPortfolioIdInOrderByExecutedAtAscCreatedAtAscIdAsc(
            Collection<UUID> portfolioIds);

    Optional<PortfolioTransactionJpaEntity> findByIdAndPortfolioId(UUID id, UUID portfolioId);

    void deleteAllByPortfolioId(UUID portfolioId);

    void deleteByIdAndPortfolioId(UUID id, UUID portfolioId);
}
