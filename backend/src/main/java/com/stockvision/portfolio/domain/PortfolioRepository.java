package com.stockvision.portfolio.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for user virtual portfolios and immutable transaction ledger entries.
 */
public interface PortfolioRepository {

    List<Portfolio> findAllByUserId(UUID userId);

    Optional<Portfolio> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

    Portfolio save(Portfolio portfolio);

    void deleteByIdAndUserId(UUID id, UUID userId);

    List<PortfolioTransaction> findTransactionsByPortfolioId(UUID portfolioId);

    List<PortfolioTransaction> findTransactionsByPortfolioIds(Collection<UUID> portfolioIds);

    Optional<PortfolioTransaction> findTransactionByIdAndPortfolioId(
            UUID transactionId, UUID portfolioId);

    PortfolioTransaction saveTransaction(PortfolioTransaction transaction);

    void deleteTransactionByIdAndPortfolioId(UUID transactionId, UUID portfolioId);
}
