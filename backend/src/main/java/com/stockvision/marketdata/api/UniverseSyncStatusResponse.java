package com.stockvision.marketdata.api;

/**
 * Summary of market data bar synchronization coverage across active exchange instruments.
 */
public record UniverseSyncStatusResponse(
        String exchange,
        String interval,
        String providerId,
        int totalActiveInstruments,
        int syncedInstruments,
        int remainingInstruments) {
}
