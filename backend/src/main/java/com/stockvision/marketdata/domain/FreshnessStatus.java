package com.stockvision.marketdata.domain;

/**
 * Explicit data freshness classification surfaced alongside market data and analysis responses.
 */
public enum FreshnessStatus {
    FRESH,
    STALE,
    SIMULATED_ONLY,
    NO_DATA
}
