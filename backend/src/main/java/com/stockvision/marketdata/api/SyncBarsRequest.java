package com.stockvision.marketdata.api;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/**
 * Admin request to synchronize historical OHLCV bars from the active {@code MarketDataProvider}.
 */
public record SyncBarsRequest(
        @NotBlank(message = "Symbol is required") String symbol,
        @NotBlank(message = "Interval is required") String interval,
        Instant from,
        Instant to,
        boolean adjusted) {
}
