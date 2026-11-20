package com.stockvision.scoring.application;

import java.math.BigDecimal;

/**
 * Query parameters for filtering, sorting, and paginating the technical screener.
 */
public record ScreenerQuery(
        String exchange,
        String interval,
        String scope,
        String query,
        BigDecimal minScore,
        BigDecimal maxScore,
        BigDecimal rsiMin,
        BigDecimal rsiMax,
        String trend,
        boolean includeInsufficientData,
        String sortBy,
        String sortDir,
        int page,
        int size) {
}
