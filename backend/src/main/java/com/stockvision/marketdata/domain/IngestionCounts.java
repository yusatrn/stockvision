package com.stockvision.marketdata.domain;

/**
 * Result counts from an idempotent bar batch insertion.
 */
public record IngestionCounts(int inserted, int skipped) {
}
