package com.stockvision.marketdata.api;

import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.marketdata.application.MarketDataIngestionService;
import com.stockvision.marketdata.application.MarketDataQueryService;
import com.stockvision.marketdata.domain.BarInterval;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for historical OHLCV market data queries and administrative provider sync.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(
        name = "Market Data",
        description = "Historical OHLCV market data bars and provider synchronization")
public class MarketDataController {

    private final MarketDataQueryService queryService;
    private final MarketDataIngestionService ingestionService;

    public MarketDataController(
            MarketDataQueryService queryService, MarketDataIngestionService ingestionService) {
        this.queryService = queryService;
        this.ingestionService = ingestionService;
    }

    @GetMapping("/instruments/{symbol}/bars")
    @Operation(
            summary = "Get historical OHLCV bars",
            description =
                    "Returns ordered UTC OHLCV bars for an instrument, including data source, simulation flag, and freshness metadata.")
    @SecurityRequirement(name = "bearerAuth")
    public HistoricalBarsResponse getHistoricalBars(
            @PathVariable String symbol,
            @RequestParam(name = "interval", defaultValue = "1d") String interval,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to,
            @RequestParam(name = "adjusted", defaultValue = "false") boolean adjusted) {
        return queryService.getHistoricalBars(symbol, interval, from, to, adjusted);
    }

    @PostMapping("/market-data/sync")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Synchronize OHLCV bars from active provider",
            description =
                    "Admin-only endpoint that triggers idempotent ingestion for a symbol and interval.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<SyncResultResponse> syncBars(@Valid @RequestBody SyncBarsRequest request) {
        BarInterval interval;
        try {
            interval = BarInterval.fromCode(request.interval());
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
        return ResponseEntity.ok(
                ingestionService.ingestBars(
                        request.symbol(),
                        interval,
                        request.from(),
                        request.to(),
                        request.adjusted()));
    }

    @GetMapping("/market-data/sync-status")
    @Operation(
            summary = "Get universe bar synchronization coverage",
            description =
                    "Reports total active instruments vs. instruments with ingested bars for the active provider.")
    @SecurityRequirement(name = "bearerAuth")
    public UniverseSyncStatusResponse getSyncStatus(
            @RequestParam(name = "exchange", defaultValue = "BIST") String exchange,
            @RequestParam(name = "interval", defaultValue = "1d") String interval) {
        return ingestionService.getUniverseSyncStatus(exchange, interval);
    }

    @PostMapping("/market-data/sync-batch")
    @Operation(
            summary = "Synchronize next batch of unsynced exchange instruments",
            description =
                    "Ingests historical bars for up to batchSize (1..50) unsynced instruments in liquidity rank order.")
    @SecurityRequirement(name = "bearerAuth")
    public BatchSyncResponse syncBatch(
            @RequestParam(name = "exchange", defaultValue = "BIST") String exchange,
            @RequestParam(name = "interval", defaultValue = "1d") String interval,
            @RequestParam(name = "batchSize", defaultValue = "25") int batchSize) {
        return ingestionService.syncNextBatch(exchange, interval, batchSize);
    }
}
