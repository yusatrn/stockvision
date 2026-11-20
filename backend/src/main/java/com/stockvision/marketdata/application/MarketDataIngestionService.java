package com.stockvision.marketdata.application;

import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.market.domain.Exchange;
import com.stockvision.market.domain.ExchangeRepository;
import com.stockvision.marketdata.api.BatchSyncResponse;
import com.stockvision.marketdata.api.SyncResultResponse;
import com.stockvision.marketdata.api.UniverseSyncStatusResponse;
import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.marketdata.domain.IngestionCounts;
import com.stockvision.marketdata.domain.MarketDataBarRepository;
import com.stockvision.marketdata.domain.MarketDataProvider;
import com.stockvision.marketdata.domain.MarketDataProviderException;
import com.stockvision.marketdata.domain.OhlcvBar;
import com.stockvision.marketdata.domain.ProviderMetadata;
import com.stockvision.marketdata.domain.ProviderSyncRun;
import com.stockvision.marketdata.domain.SyncRunStatus;
import com.stockvision.marketdata.infrastructure.MarketDataProperties;
import com.stockvision.stock.domain.Instrument;
import com.stockvision.stock.domain.InstrumentRepository;
import com.stockvision.stock.domain.SymbolNormalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinates idempotent historical OHLCV bar ingestion from {@link MarketDataProvider} into
 * PostgreSQL and records {@link ProviderSyncRun} audit entries.
 */
@Service
public class MarketDataIngestionService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private static final int MAX_BATCH_SIZE = 50;
    private static final int SYNC_CONCURRENCY = 4;

    private final ExchangeRepository exchangeRepository;
    private final InstrumentRepository instrumentRepository;
    private final MarketDataProvider marketDataProvider;
    private final MarketDataBarRepository marketDataBarRepository;
    private final SyncRunAuditService syncRunAuditService;
    private final MarketDataProperties properties;
    private final Clock clock;

    public MarketDataIngestionService(
            ExchangeRepository exchangeRepository,
            InstrumentRepository instrumentRepository,
            MarketDataProvider marketDataProvider,
            MarketDataBarRepository marketDataBarRepository,
            SyncRunAuditService syncRunAuditService,
            MarketDataProperties properties,
            Clock clock) {
        this.exchangeRepository = exchangeRepository;
        this.instrumentRepository = instrumentRepository;
        this.marketDataProvider = marketDataProvider;
        this.marketDataBarRepository = marketDataBarRepository;
        this.syncRunAuditService = syncRunAuditService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public SyncResultResponse ingestBars(
            String rawSymbol, BarInterval interval, Instant from, Instant to, boolean adjusted) {
        String normalizedSymbol = normalizeSymbol(rawSymbol);
        DateRange range = resolveAndValidateRange(interval, from, to);

        Instrument instrument =
                instrumentRepository
                        .findBySymbol(normalizedSymbol)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.NOT_FOUND,
                                                "Instrument not found: " + normalizedSymbol));

        return ingestInstrumentRange(instrument, interval, range, adjusted);
    }

    public UniverseSyncStatusResponse getUniverseSyncStatus(String rawExchange, String rawInterval) {
        Exchange exchange = resolveExchange(rawExchange);
        BarInterval interval = parseInterval(rawInterval);
        ProviderMetadata metadata = marketDataProvider.getProviderMetadata();
        DateRange range = resolveAndValidateRange(interval, null, null);

        List<Instrument> activeInstruments =
                instrumentRepository.findActiveByExchangeCode(exchange.code());
        Set<UUID> syncedIds =
                marketDataBarRepository.findSyncedInstrumentIds(
                        interval, metadata.providerId(), false, range.from(), range.to());
        int syncedCount =
                (int) activeInstruments.stream().filter(i -> syncedIds.contains(i.id())).count();
        int totalActive = activeInstruments.size();
        return new UniverseSyncStatusResponse(
                exchange.code(),
                interval.code(),
                metadata.providerId(),
                totalActive,
                syncedCount,
                Math.max(0, totalActive - syncedCount));
    }

    public BatchSyncResponse syncNextBatch(String rawExchange, String rawInterval, int batchSize) {
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "batchSize must be between 1 and " + MAX_BATCH_SIZE);
        }
        Exchange exchange = resolveExchange(rawExchange);
        BarInterval interval = parseInterval(rawInterval);
        ProviderMetadata metadata = marketDataProvider.getProviderMetadata();
        DateRange range = resolveAndValidateRange(interval, null, null);

        List<Instrument> activeInstruments =
                instrumentRepository.findActiveByExchangeCode(exchange.code());
        Set<UUID> initialSyncedIds =
                marketDataBarRepository.findSyncedInstrumentIds(
                        interval, metadata.providerId(), false, range.from(), range.to());

        List<Instrument> targets =
                activeInstruments.stream()
                        .filter(i -> !initialSyncedIds.contains(i.id()))
                        .limit(batchSize)
                        .toList();

        List<String> syncedSymbols = new ArrayList<>();
        List<String> failedSymbols = new ArrayList<>();

        if (!targets.isEmpty()) {
            int threads = Math.min(SYNC_CONCURRENCY, targets.size());
            try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
                List<Future<Boolean>> futures = new ArrayList<>(targets.size());
                for (Instrument target : targets) {
                    futures.add(
                            executor.submit(
                                    () -> {
                                        try {
                                            ingestInstrumentRange(target, interval, range, false);
                                            return true;
                                        } catch (AppException | MarketDataProviderException ex) {
                                            return false;
                                        }
                                    }));
                }
                for (int i = 0; i < targets.size(); i++) {
                    String sym = targets.get(i).symbol();
                    try {
                        if (Boolean.TRUE.equals(futures.get(i).get())) {
                            syncedSymbols.add(sym);
                        } else {
                            failedSymbols.add(sym);
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        failedSymbols.add(sym);
                    } catch (ExecutionException ex) {
                        failedSymbols.add(sym);
                    }
                }
            }
        }

        Set<UUID> finalSyncedIds =
                marketDataBarRepository.findSyncedInstrumentIds(
                        interval, metadata.providerId(), false, range.from(), range.to());
        int syncedCount =
                (int) activeInstruments.stream().filter(i -> finalSyncedIds.contains(i.id())).count();
        int totalActive = activeInstruments.size();

        return new BatchSyncResponse(
                exchange.code(),
                interval.code(),
                metadata.providerId(),
                totalActive,
                syncedCount,
                Math.max(0, totalActive - syncedCount),
                targets.size(),
                syncedSymbols.size(),
                failedSymbols.size(),
                List.copyOf(syncedSymbols),
                List.copyOf(failedSymbols));
    }

    private SyncResultResponse ingestInstrumentRange(
            Instrument instrument, BarInterval interval, DateRange range, boolean adjusted) {
        ProviderMetadata metadata = marketDataProvider.getProviderMetadata();
        Instant startedAt = clock.instant();

        try {
            List<OhlcvBar> bars =
                    marketDataProvider.getHistoricalBars(
                            instrument.symbol(),
                            interval,
                            range.from(),
                            range.to(),
                            adjusted);

            validateProviderBars(bars, interval, metadata.providerId(), adjusted);
            IngestionCounts counts =
                    marketDataBarRepository.saveAllIdempotent(instrument.id(), bars);
            Instant completedAt = clock.instant();

            ProviderSyncRun savedRun =
                    syncRunAuditService.record(
                            new ProviderSyncRun(
                                    UUID.randomUUID(),
                                    metadata.providerId(),
                                    instrument.id(),
                                    instrument.symbol(),
                                    interval,
                                    adjusted,
                                    metadata.simulated(),
                                    SyncRunStatus.SUCCESS,
                                    bars.size(),
                                    counts.inserted(),
                                    counts.skipped(),
                                    range.from(),
                                    range.to(),
                                    startedAt,
                                    completedAt,
                                    null));

            return SyncResultResponse.from(savedRun);
        } catch (MarketDataProviderException | IllegalArgumentException ex) {
            Instant completedAt = clock.instant();
            syncRunAuditService.record(
                    new ProviderSyncRun(
                            UUID.randomUUID(),
                            metadata.providerId(),
                            instrument.id(),
                            instrument.symbol(),
                            interval,
                            adjusted,
                            metadata.simulated(),
                            SyncRunStatus.FAILED,
                            0,
                            0,
                            0,
                            range.from(),
                            range.to(),
                            startedAt,
                            completedAt,
                            truncate(ex.getMessage())));
            throw new AppException(
                    ErrorCode.PROVIDER_UNAVAILABLE,
                    "Market data provider failed to supply valid bars",
                    ex);
        }
    }

    private Exchange resolveExchange(String rawExchange) {
        String exchangeCode =
                (rawExchange == null || rawExchange.isBlank())
                        ? "BIST"
                        : rawExchange.trim().toUpperCase(Locale.ROOT);
        return exchangeRepository
                .findByCode(exchangeCode)
                .orElseThrow(
                        () ->
                                new AppException(
                                        ErrorCode.NOT_FOUND,
                                        "Exchange not found: " + exchangeCode));
    }

    private static BarInterval parseInterval(String rawInterval) {
        try {
            return BarInterval.fromCode(rawInterval);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
    }

    public DateRange resolveAndValidateRange(BarInterval interval, Instant from, Instant to) {
        Instant effectiveTo = to != null ? to : clock.instant();
        int defaultLookbackDays =
                Math.min(properties.getDefaultLookbackDays(), interval.maxRangeDays());
        Instant effectiveFrom =
                from != null ? from : effectiveTo.minus(Duration.ofDays(defaultLookbackDays));

        if (!effectiveFrom.isBefore(effectiveTo)) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR, "'from' timestamp must be strictly before 'to'");
        }
        Duration requestedSpan = Duration.between(effectiveFrom, effectiveTo);
        Duration maxAllowedSpan = Duration.ofDays(interval.maxRangeDays());
        if (requestedSpan.compareTo(maxAllowedSpan) > 0) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Requested date range exceeds maximum of "
                            + interval.maxRangeDays()
                            + " days for interval "
                            + interval.code());
        }
        return new DateRange(effectiveFrom, effectiveTo);
    }

    private static String normalizeSymbol(String rawSymbol) {
        try {
            return SymbolNormalizer.normalize(rawSymbol);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
    }

    private static void validateProviderBars(
            List<OhlcvBar> bars, BarInterval expectedInterval, String expectedProvider, boolean expectedAdjusted) {
        if (bars == null) {
            throw new IllegalArgumentException("Provider returned null bar list");
        }
        for (OhlcvBar bar : bars) {
            if (bar == null
                    || bar.interval() != expectedInterval
                    || !expectedProvider.equals(bar.provider())
                    || bar.adjusted() != expectedAdjusted) {
                throw new IllegalArgumentException(
                        "Provider returned bar with mismatched interval, provider, or adjustment flag");
            }
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return "Provider error";
        }
        return message.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    /**
     * Resolved and validated inclusive UTC query range.
     */
    public record DateRange(Instant from, Instant to) {
    }
}
