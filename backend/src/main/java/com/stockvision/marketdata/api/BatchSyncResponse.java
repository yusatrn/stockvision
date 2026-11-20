package com.stockvision.marketdata.api;

import java.util.List;

/**
 * Result of synchronizing a bounded batch of currently unsynced exchange instruments.
 */
public record BatchSyncResponse(
        String exchange,
        String interval,
        String providerId,
        int totalActiveInstruments,
        int syncedInstruments,
        int remainingInstruments,
        int processedInBatch,
        int succeededInBatch,
        int failedInBatch,
        List<String> syncedSymbols,
        List<String> failedSymbols) {
}
