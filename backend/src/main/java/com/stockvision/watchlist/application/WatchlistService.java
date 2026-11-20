package com.stockvision.watchlist.application;

import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import com.stockvision.marketdata.api.FreshnessMetadataResponse;
import com.stockvision.marketdata.application.FreshnessEvaluator;
import com.stockvision.marketdata.domain.LatestQuote;
import com.stockvision.marketdata.domain.MarketDataProvider;
import com.stockvision.marketdata.domain.MarketDataProviderException;
import com.stockvision.marketdata.domain.ProviderMetadata;
import com.stockvision.stock.domain.Instrument;
import com.stockvision.stock.domain.InstrumentRepository;
import com.stockvision.stock.domain.SymbolNormalizer;
import com.stockvision.watchlist.api.AddWatchlistItemRequest;
import com.stockvision.watchlist.api.CreateWatchlistRequest;
import com.stockvision.watchlist.api.UpdateWatchlistRequest;
import com.stockvision.watchlist.api.WatchlistCollectionResponse;
import com.stockvision.watchlist.api.WatchlistItemQuoteResponse;
import com.stockvision.watchlist.api.WatchlistItemResponse;
import com.stockvision.watchlist.api.WatchlistResponse;
import com.stockvision.watchlist.domain.Watchlist;
import com.stockvision.watchlist.domain.WatchlistItem;
import com.stockvision.watchlist.domain.WatchlistRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service managing user-owned watchlists and duplicate-free instrument memberships.
 */
@Service
public class WatchlistService {

    private static final Logger log = LoggerFactory.getLogger(WatchlistService.class);

    private final WatchlistRepository watchlistRepository;
    private final InstrumentRepository instrumentRepository;
    private final MarketDataProvider marketDataProvider;
    private final FreshnessEvaluator freshnessEvaluator;
    private final Clock clock;

    public WatchlistService(
            WatchlistRepository watchlistRepository,
            InstrumentRepository instrumentRepository,
            MarketDataProvider marketDataProvider,
            FreshnessEvaluator freshnessEvaluator,
            Clock clock) {
        this.watchlistRepository = watchlistRepository;
        this.instrumentRepository = instrumentRepository;
        this.marketDataProvider = marketDataProvider;
        this.freshnessEvaluator = freshnessEvaluator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WatchlistCollectionResponse listWatchlists(UUID userId) {
        List<Watchlist> watchlists = watchlistRepository.findAllByUserId(userId);
        List<UUID> listIds = watchlists.stream().map(Watchlist::id).toList();
        List<WatchlistItem> allItems = watchlistRepository.findItemsByWatchlistIds(listIds);

        Set<UUID> instrumentIds =
                allItems.stream().map(WatchlistItem::instrumentId).collect(Collectors.toSet());
        Map<UUID, Instrument> instrumentsById =
                instrumentRepository.findAllByIds(instrumentIds).stream()
                        .collect(Collectors.toMap(Instrument::id, Function.identity()));

        ProviderMetadata metadata = marketDataProvider.getProviderMetadata();
        Map<String, LatestQuote> quoteCache = new HashMap<>();
        Instant latestQuoteAt = null;

        for (Instrument instrument : instrumentsById.values()) {
            LatestQuote quote = safeLatestQuote(instrument.symbol());
            if (quote != null) {
                quoteCache.put(instrument.symbol(), quote);
                if (latestQuoteAt == null || quote.quotedAt().isAfter(latestQuoteAt)) {
                    latestQuoteAt = quote.quotedAt();
                }
            }
        }

        Map<UUID, List<WatchlistItem>> itemsByListId =
                allItems.stream().collect(Collectors.groupingBy(WatchlistItem::watchlistId));

        List<WatchlistResponse> responses =
                watchlists.stream()
                        .map(
                                list ->
                                        toWatchlistResponse(
                                                list,
                                                itemsByListId.getOrDefault(list.id(), List.of()),
                                                instrumentsById,
                                                quoteCache))
                        .toList();

        FreshnessMetadataResponse freshness =
                freshnessEvaluator.evaluate(metadata, latestQuoteAt, Optional.empty());

        return new WatchlistCollectionResponse(
                responses, DataSourceMetadataResponse.from(metadata), freshness);
    }

    @Transactional
    public WatchlistResponse createWatchlist(UUID userId, CreateWatchlistRequest request) {
        String name = normalizeRequiredText(request.name(), "Watchlist name is required");
        String description = normalizeOptionalText(request.description());

        if (watchlistRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new AppException(
                    ErrorCode.CONFLICT, "Watchlist with name '" + name + "' already exists");
        }

        Instant now = clock.instant();
        Watchlist toSave =
                new Watchlist(UUID.randomUUID(), userId, name, description, now, now);

        try {
            Watchlist saved = watchlistRepository.save(toSave);
            return toWatchlistResponse(saved, List.of(), Map.of(), Map.of());
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(
                    ErrorCode.CONFLICT, "Watchlist with name '" + name + "' already exists", ex);
        }
    }

    @Transactional
    public WatchlistResponse updateWatchlist(
            UUID userId, UUID watchlistId, UpdateWatchlistRequest request) {
        Watchlist existing = requireOwnedWatchlist(watchlistId, userId);
        String name = normalizeRequiredText(request.name(), "Watchlist name is required");
        String description = normalizeOptionalText(request.description());

        if (watchlistRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(
                userId, name, watchlistId)) {
            throw new AppException(
                    ErrorCode.CONFLICT, "Watchlist with name '" + name + "' already exists");
        }

        Watchlist updatedDomain =
                new Watchlist(
                        existing.id(),
                        existing.userId(),
                        name,
                        description,
                        existing.createdAt(),
                        clock.instant());
        try {
            Watchlist saved = watchlistRepository.save(updatedDomain);
            List<WatchlistItem> items = watchlistRepository.findItemsByWatchlistIds(List.of(saved.id()));
            Map<UUID, Instrument> instrumentsById = loadInstrumentMap(items);
            Map<String, LatestQuote> quoteCache = loadQuoteMap(instrumentsById);
            return toWatchlistResponse(saved, items, instrumentsById, quoteCache);
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(
                    ErrorCode.CONFLICT, "Watchlist with name '" + name + "' already exists", ex);
        }
    }

    @Transactional
    public void deleteWatchlist(UUID userId, UUID watchlistId) {
        Watchlist existing = requireOwnedWatchlist(watchlistId, userId);
        watchlistRepository.deleteByIdAndUserId(existing.id(), userId);
    }

    @Transactional
    public WatchlistItemResponse addItem(
            UUID userId, UUID watchlistId, AddWatchlistItemRequest request) {
        Watchlist watchlist = requireOwnedWatchlist(watchlistId, userId);
        String normalizedSymbol = normalizeSymbol(request.symbol());
        Instrument instrument =
                instrumentRepository
                        .findBySymbol(normalizedSymbol)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.NOT_FOUND,
                                                "Instrument not found: " + normalizedSymbol));

        if (watchlistRepository.existsItemByWatchlistIdAndInstrumentId(
                watchlist.id(), instrument.id())) {
            throw new AppException(
                    ErrorCode.CONFLICT,
                    "Instrument '" + normalizedSymbol + "' is already present in this watchlist");
        }

        WatchlistItem item =
                new WatchlistItem(
                        UUID.randomUUID(),
                        watchlist.id(),
                        instrument.id(),
                        normalizeOptionalText(request.notes()),
                        clock.instant());
        try {
            WatchlistItem saved = watchlistRepository.saveItem(item);
            LatestQuote quote = safeLatestQuote(instrument.symbol());
            return new WatchlistItemResponse(
                    saved.id(),
                    instrument.id(),
                    instrument.symbol(),
                    instrument.name(),
                    instrument.exchangeCode(),
                    instrument.sector(),
                    instrument.currency(),
                    saved.notes(),
                    saved.createdAt(),
                    quote != null ? WatchlistItemQuoteResponse.from(quote) : null);
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(
                    ErrorCode.CONFLICT,
                    "Instrument '" + normalizedSymbol + "' is already present in this watchlist",
                    ex);
        }
    }

    @Transactional
    public void removeItem(UUID userId, UUID watchlistId, UUID itemId) {
        Watchlist watchlist = requireOwnedWatchlist(watchlistId, userId);
        WatchlistItem item =
                watchlistRepository
                        .findItemByIdAndWatchlistId(itemId, watchlist.id())
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.NOT_FOUND, "Watchlist item not found"));
        watchlistRepository.deleteItemByIdAndWatchlistId(item.id(), watchlist.id());
    }

    private Watchlist requireOwnedWatchlist(UUID watchlistId, UUID userId) {
        return watchlistRepository
                .findByIdAndUserId(watchlistId, userId)
                .orElseThrow(
                        () -> new AppException(ErrorCode.NOT_FOUND, "Watchlist not found"));
    }

    private Map<UUID, Instrument> loadInstrumentMap(List<WatchlistItem> items) {
        Set<UUID> ids = items.stream().map(WatchlistItem::instrumentId).collect(Collectors.toSet());
        return instrumentRepository.findAllByIds(ids).stream()
                .collect(Collectors.toMap(Instrument::id, Function.identity()));
    }

    private Map<String, LatestQuote> loadQuoteMap(Map<UUID, Instrument> instrumentsById) {
        Map<String, LatestQuote> quotes = new HashMap<>();
        for (Instrument instrument : instrumentsById.values()) {
            LatestQuote q = safeLatestQuote(instrument.symbol());
            if (q != null) {
                quotes.put(instrument.symbol(), q);
            }
        }
        return quotes;
    }

    private LatestQuote safeLatestQuote(String symbol) {
        try {
            return marketDataProvider.getLatestQuote(symbol);
        } catch (MarketDataProviderException | IllegalStateException ex) {
            log.warn("Quote lookup failed for watchlist symbol {}: {}", symbol, ex.getMessage());
            return null;
        }
    }

    private WatchlistResponse toWatchlistResponse(
            Watchlist watchlist,
            List<WatchlistItem> items,
            Map<UUID, Instrument> instrumentsById,
            Map<String, LatestQuote> quoteCache) {
        List<WatchlistItemResponse> itemResponses =
                items.stream()
                        .sorted(Comparator.comparing(WatchlistItem::createdAt))
                        .map(
                                item -> {
                                    Instrument instrument = instrumentsById.get(item.instrumentId());
                                    if (instrument == null) {
                                        return null;
                                    }
                                    LatestQuote quote = quoteCache.get(instrument.symbol());
                                    return new WatchlistItemResponse(
                                            item.id(),
                                            instrument.id(),
                                            instrument.symbol(),
                                            instrument.name(),
                                            instrument.exchangeCode(),
                                            instrument.sector(),
                                            instrument.currency(),
                                            item.notes(),
                                            item.createdAt(),
                                            quote != null
                                                    ? WatchlistItemQuoteResponse.from(quote)
                                                    : null);
                                })
                        .filter(Objects::nonNull)
                        .toList();

        return new WatchlistResponse(
                watchlist.id(),
                watchlist.name(),
                watchlist.description(),
                watchlist.createdAt(),
                watchlist.updatedAt(),
                itemResponses);
    }

    private static String normalizeSymbol(String rawSymbol) {
        try {
            return SymbolNormalizer.normalize(rawSymbol);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
    }

    private static String normalizeRequiredText(String raw, String message) {
        if (raw == null || raw.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, message);
        }
        return raw.trim();
    }

    private static String normalizeOptionalText(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }
}
