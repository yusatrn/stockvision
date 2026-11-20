package com.stockvision.portfolio.infrastructure;

import com.stockvision.portfolio.domain.Portfolio;
import com.stockvision.portfolio.domain.PortfolioRepository;
import com.stockvision.portfolio.domain.PortfolioTransaction;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Infrastructure adapter bridging {@link PortfolioRepository} to Spring Data JPA.
 */
@Repository
public class JpaPortfolioRepositoryAdapter implements PortfolioRepository {

    private final SpringDataPortfolioRepository portfolioRepository;
    private final SpringDataPortfolioTransactionRepository transactionRepository;

    public JpaPortfolioRepositoryAdapter(
            SpringDataPortfolioRepository portfolioRepository,
            SpringDataPortfolioTransactionRepository transactionRepository) {
        this.portfolioRepository = portfolioRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public List<Portfolio> findAllByUserId(UUID userId) {
        return portfolioRepository.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(PortfolioJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Portfolio> findByIdAndUserId(UUID id, UUID userId) {
        return portfolioRepository.findByIdAndUserId(id, userId).map(PortfolioJpaEntity::toDomain);
    }

    @Override
    public boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name) {
        return portfolioRepository.existsByUserIdAndNameIgnoreCase(userId, name);
    }

    @Override
    public Portfolio save(Portfolio portfolio) {
        return portfolioRepository.saveAndFlush(PortfolioJpaEntity.fromDomain(portfolio)).toDomain();
    }

    @Override
    public void deleteByIdAndUserId(UUID id, UUID userId) {
        transactionRepository.deleteAllByPortfolioId(id);
        portfolioRepository.deleteByIdAndUserId(id, userId);
    }

    @Override
    public List<PortfolioTransaction> findTransactionsByPortfolioId(UUID portfolioId) {
        return transactionRepository
                .findAllByPortfolioIdOrderByExecutedAtAscCreatedAtAscIdAsc(portfolioId)
                .stream()
                .map(PortfolioTransactionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<PortfolioTransaction> findTransactionsByPortfolioIds(Collection<UUID> portfolioIds) {
        if (portfolioIds == null || portfolioIds.isEmpty()) {
            return List.of();
        }
        return transactionRepository
                .findAllByPortfolioIdInOrderByExecutedAtAscCreatedAtAscIdAsc(portfolioIds)
                .stream()
                .map(PortfolioTransactionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<PortfolioTransaction> findTransactionByIdAndPortfolioId(
            UUID transactionId, UUID portfolioId) {
        return transactionRepository
                .findByIdAndPortfolioId(transactionId, portfolioId)
                .map(PortfolioTransactionJpaEntity::toDomain);
    }

    @Override
    public PortfolioTransaction saveTransaction(PortfolioTransaction transaction) {
        return transactionRepository
                .saveAndFlush(PortfolioTransactionJpaEntity.fromDomain(transaction))
                .toDomain();
    }

    @Override
    public void deleteTransactionByIdAndPortfolioId(UUID transactionId, UUID portfolioId) {
        transactionRepository.deleteByIdAndPortfolioId(transactionId, portfolioId);
    }
}
