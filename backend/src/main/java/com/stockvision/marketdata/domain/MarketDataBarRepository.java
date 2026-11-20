package com.stockvision.marketdata.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Persistence port for OHLCV market data bars.
 */
public interface MarketDataBarRepository {

    List<OhlcvBar> findBars(
            UUID instrumentId,
            BarInterval interval,
            String provider,
            boolean adjusted,
            Instant from,
            Instant to);

    Optional<OhlcvBar> findLatestBar(
            UUID instrumentId, BarInterval interval, String provider, boolean adjusted);

    Set<UUID> findSyncedInstrumentIds(
            BarInterval interval, String provider, boolean adjusted, Instant from, Instant to);

    IngestionCounts saveAllIdempotent(UUID instrumentId, List<OhlcvBar> bars);
}
