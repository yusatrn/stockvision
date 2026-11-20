package com.stockvision.scoring.application;

import java.math.BigDecimal;

/**
 * Query parameters for filtering, sorting, and paginating momentum and technical setup candidates.
 */
public record OpportunitiesQuery(
        String exchange,
        String interval,
        String scope,
        String query,
        String category,
        BigDecimal minMomentumScore,
        boolean includeInsufficientData,
        String sortBy,
        String sortDir,
        int page,
        int size) {
}
