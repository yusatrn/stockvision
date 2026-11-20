package com.stockvision.portfolio.api;

import com.stockvision.portfolio.domain.FeeTaxPolicy;
import java.time.Instant;
import java.util.UUID;

/**
 * Virtual portfolio metadata with derived transaction and open-position counts.
 */
public record PortfolioSummaryResponse(
        UUID id,
        String name,
        String description,
        String baseCurrency,
        FeeTaxPolicy feeTaxPolicy,
        int openPositionsCount,
        int transactionCount,
        Instant createdAt,
        Instant updatedAt) {
}
