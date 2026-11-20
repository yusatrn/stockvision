package com.stockvision.portfolio.domain;

/**
 * Honest market valuation status for derived virtual portfolio holdings.
 */
public enum PortfolioValuationStatus {
    READY,
    EMPTY,
    STALE_DATA,
    PARTIAL_UNAVAILABLE
}
