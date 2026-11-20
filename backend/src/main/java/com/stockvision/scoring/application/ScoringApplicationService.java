package com.stockvision.scoring.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.market.domain.Exchange;
import com.stockvision.market.domain.ExchangeRepository;
import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import com.stockvision.marketdata.api.FreshnessMetadataResponse;
import com.stockvision.marketdata.application.FreshnessEvaluator;
import com.stockvision.marketdata.application.MarketDataIngestionService;
import com.stockvision.marketdata.application.MarketDataIngestionService.DateRange;
import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.marketdata.domain.LatestQuote;
import com.stockvision.marketdata.domain.MarketDataBarRepository;
import com.stockvision.marketdata.domain.MarketDataProvider;
import com.stockvision.marketdata.domain.MarketDataProviderException;
import com.stockvision.marketdata.domain.OhlcvBar;
import com.stockvision.marketdata.domain.ProviderMetadata;
import com.stockvision.marketdata.domain.ProviderSyncRun;
import com.stockvision.marketdata.domain.ProviderSyncRunRepository;
import com.stockvision.scoring.api.BistMarginReferenceResponse;
import com.stockvision.scoring.api.InstrumentAnalysisResponse;
import com.stockvision.scoring.api.InstrumentSetupResponse;
import com.stockvision.scoring.api.OpportunitiesPageResponse;
import com.stockvision.scoring.api.OpportunityItemResponse;
import com.stockvision.scoring.api.ScoreComponentResponse;
import com.stockvision.scoring.api.ScoreExplanationResponse;
import com.stockvision.scoring.api.ScreenerItemResponse;
import com.stockvision.scoring.api.ScreenerPageResponse;
import com.stockvision.scoring.domain.BistMarginReference;
import com.stockvision.scoring.domain.ScoreComponentType;
import com.stockvision.scoring.domain.ScoreExplanation;
import com.stockvision.scoring.domain.ScoreRegime;
import com.stockvision.scoring.domain.ScoreStatus;
import com.stockvision.scoring.domain.ScoreWeights;
import com.stockvision.scoring.domain.SetupCategory;
import com.stockvision.scoring.domain.TechnicalScoreResult;
import com.stockvision.scoring.domain.TechnicalScoreRun;
import com.stockvision.scoring.domain.TechnicalScoreRunRepository;
import com.stockvision.scoring.domain.TechnicalScoringService;
import com.stockvision.scoring.domain.TechnicalSetupResult;
import com.stockvision.scoring.domain.TechnicalSetupService;
import com.stockvision.scoring.domain.TrendDirection;
import com.stockvision.scoring.domain.UniverseScope;
import com.stockvision.scoring.infrastructure.ScoringProperties;
import com.stockvision.stock.domain.Instrument;
import com.stockvision.stock.domain.InstrumentRepository;
import com.stockvision.stock.domain.SymbolNormalizer;
import com.stockvision.technicalanalysis.domain.AnalysisRun;
import com.stockvision.technicalanalysis.domain.AnalysisRunRepository;
import com.stockvision.technicalanalysis.domain.AnalysisRunStatus;
import com.stockvision.technicalanalysis.domain.AnalysisRunType;
import com.stockvision.technicalanalysis.domain.IndicatorConfiguration;
import com.stockvision.technicalanalysis.domain.IndicatorType;
import com.stockvision.technicalanalysis.domain.TechnicalAnalysisResult;
import com.stockvision.technicalanalysis.domain.TechnicalIndicatorService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.stereotype.Service;

/**
 * Coordinates explainable technical score calculation, persistence, and multi-instrument screening.
 */
@Service
public class ScoringApplicationService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final String SCREENER_DISCLAIMER =
            "Tarayıcı sıralamaları yalnızca geçmiş veya simüle teknik göstergeleri özetler. "
                    + "Gelecekteki getirileri öngörmez ve yatırım tavsiyesi niteliği taşımaz.";
    private static final String OPPORTUNITIES_DISCLAIMER =
            "Aday sıralamaları ve teknik seviyeler tavan tahmini veya al/sat yatırım tavsiyesi değildir; "
                    + "geçmiş ve gecikmeli veriler üzerinden kural tabanlı karar destek referansı sunar.";

    private final InstrumentRepository instrumentRepository;
    private final ExchangeRepository exchangeRepository;
    private final MarketDataProvider marketDataProvider;
    private final MarketDataBarRepository marketDataBarRepository;
    private final ProviderSyncRunRepository providerSyncRunRepository;
    private final MarketDataIngestionService ingestionService;
    private final FreshnessEvaluator freshnessEvaluator;
    private final TechnicalIndicatorService indicatorService;
    private final AnalysisRunRepository analysisRunRepository;
    private final TechnicalScoringService scoringService;
    private final TechnicalSetupService setupService;
    private final TechnicalScoreRunRepository scoreRunRepository;
    private final ScoringProperties scoringProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ScoringApplicationService(
            InstrumentRepository instrumentRepository,
            ExchangeRepository exchangeRepository,
            MarketDataProvider marketDataProvider,
            MarketDataBarRepository marketDataBarRepository,
            ProviderSyncRunRepository providerSyncRunRepository,
            MarketDataIngestionService ingestionService,
            FreshnessEvaluator freshnessEvaluator,
            TechnicalIndicatorService indicatorService,
            AnalysisRunRepository analysisRunRepository,
            TechnicalScoringService scoringService,
            TechnicalSetupService setupService,
            TechnicalScoreRunRepository scoreRunRepository,
            ScoringProperties scoringProperties,
            ObjectMapper objectMapper,
            Clock clock) {
        this.instrumentRepository = instrumentRepository;
        this.exchangeRepository = exchangeRepository;
        this.marketDataProvider = marketDataProvider;
        this.marketDataBarRepository = marketDataBarRepository;
        this.providerSyncRunRepository = providerSyncRunRepository;
        this.ingestionService = ingestionService;
        this.freshnessEvaluator = freshnessEvaluator;
        this.indicatorService = indicatorService;
        this.analysisRunRepository = analysisRunRepository;
        this.scoringService = scoringService;
        this.setupService = setupService;
        this.scoreRunRepository = scoreRunRepository;
        this.scoringProperties = scoringProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public InstrumentAnalysisResponse analyzeInstrument(
            String rawSymbol, String rawInterval, Instant from, Instant to, boolean adjusted) {
        String normalizedSymbol = normalizeSymbol(rawSymbol);
        BarInterval interval = parseInterval(rawInterval);
        DateRange range = ingestionService.resolveAndValidateRange(interval, from, to);

        Instrument instrument = findInstrument(normalizedSymbol);

        ProviderMetadata metadata = marketDataProvider.getProviderMetadata();
        List<OhlcvBar> bars = loadBarsOrIngest(instrument, interval, range, metadata, adjusted);

        TechnicalAnalysisResult indicatorResult =
                indicatorService.calculate(
                        bars,
                        Arrays.asList(IndicatorType.values()),
                        IndicatorConfiguration.defaults());

        ScoreWeights weights = scoringProperties.toScoreWeights();
        TechnicalScoreResult scoreResult =
                scoringService.score(
                        instrument.symbol(),
                        bars,
                        indicatorResult,
                        weights,
                        range.to(),
                        scoringProperties.getMaxDataAgeHours(),
                        scoringProperties.isRejectStaleData(),
                        metadata.simulated());

        Instant latestBarTimestamp = bars.isEmpty() ? null : bars.getLast().timestamp();
        Instant calculatedAt = clock.instant();
        String weightsJson = serializeJson(weightsToStringMap(weights));
        String componentsJson = serializeJson(scoreResult.components());
        String explanationJson = serializeJson(scoreResult.explanation());

        AnalysisRunStatus runStatus =
                scoreResult.status() == ScoreStatus.READY
                        ? AnalysisRunStatus.READY
                        : AnalysisRunStatus.INSUFFICIENT_DATA;

        AnalysisRun savedAnalysisRun =
                analysisRunRepository.save(
                        new AnalysisRun(
                                UUID.randomUUID(),
                                instrument.id(),
                                instrument.symbol(),
                                interval,
                                metadata.providerId(),
                                adjusted,
                                metadata.simulated(),
                                AnalysisRunType.TECHNICAL_SCORE,
                                scoreResult.algorithmVersion(),
                                runStatus,
                                weightsJson,
                                bars.size(),
                                range.from(),
                                range.to(),
                                latestBarTimestamp,
                                calculatedAt));

        TechnicalScoreRun savedScoreRun =
                scoreRunRepository.save(
                        new TechnicalScoreRun(
                                UUID.randomUUID(),
                                savedAnalysisRun.id(),
                                instrument.id(),
                                instrument.symbol(),
                                instrument.exchangeCode(),
                                interval,
                                metadata.providerId(),
                                adjusted,
                                metadata.simulated(),
                                scoreResult.algorithmVersion(),
                                scoreResult.status(),
                                scoreResult.staleData(),
                                scoreResult.finalScore(),
                                scoreResult.regime(),
                                scoreResult.trendDirection(),
                                scoreResult.latestClose(),
                                scoreResult.latestRsi(),
                                weightsJson,
                                componentsJson,
                                explanationJson,
                                range.from(),
                                range.to(),
                                latestBarTimestamp,
                                calculatedAt));

        Optional<ProviderSyncRun> latestSync =
                providerSyncRunRepository.findLatestByInstrumentAndInterval(
                        metadata.providerId(), instrument.id(), interval, adjusted);
        FreshnessMetadataResponse freshness =
                freshnessEvaluator.evaluate(metadata, latestBarTimestamp, latestSync);

        return new InstrumentAnalysisResponse(
                savedScoreRun.id(),
                savedAnalysisRun.id(),
                instrument.symbol(),
                instrument.exchangeCode(),
                interval.code(),
                adjusted,
                range.from(),
                range.to(),
                bars.size(),
                scoreResult.algorithmVersion(),
                scoreResult.indicatorEngineVersion(),
                scoreResult.status(),
                scoreResult.staleData(),
                scoreResult.finalScore(),
                scoreResult.regime(),
                scoreResult.trendDirection(),
                scoreResult.latestClose(),
                scoreResult.latestRsi(),
                scoreResult.insufficientDataReasons(),
                weightsToStringMap(weights),
                scoreResult.components().stream().map(ScoreComponentResponse::from).toList(),
                ScoreExplanationResponse.from(scoreResult.explanation()),
                calculatedAt,
                DataSourceMetadataResponse.from(metadata),
                freshness);
    }

    public ScreenerPageResponse screen(ScreenerQuery query) {
        validatePagination(query.page(), query.size());
        validateBounds("minScore", "maxScore", query.minScore(), query.maxScore());
        validateBounds("rsiMin", "rsiMax", query.rsiMin(), query.rsiMax());
        UniverseScope scope = parseUniverseScope(query.scope());
        TrendDirection trendFilter = parseTrendFilter(query.trend());
        String sortBy = parseSortBy(query.sortBy());
        boolean ascending = parseSortAscending(query.sortDir());
        BarInterval interval = parseInterval(query.interval());

        Exchange exchange = resolveExchange(query.exchange());

        ProviderMetadata metadata = marketDataProvider.getProviderMetadata();
        ScoreWeights weights = scoringProperties.toScoreWeights();
        String expectedWeightsJson = serializeJson(weightsToStringMap(weights));
        Instant now = clock.instant();
        DateRange range = ingestionService.resolveAndValidateRange(interval, null, null);

        Set<UUID> initialSyncedIds =
                marketDataBarRepository.findSyncedInstrumentIds(
                        interval, metadata.providerId(), false, range.from(), range.to());
        List<Instrument> instruments =
                resolveTargetInstruments(exchange, scope, query.query(), initialSyncedIds);
        Set<UUID> effectiveSyncedIds =
                preIngestUnsyncedSafely(
                        instruments,
                        initialSyncedIds,
                        interval,
                        range,
                        metadata,
                        scope,
                        query.query());

        Instant maxLatestBarTimestamp = null;
        List<ScreenerItemResponse> candidates = new java.util.ArrayList<>(instruments.size());
        for (Instrument instrument : instruments) {
            ScreenerCandidate candidate =
                    resolveCandidate(
                            instrument,
                            interval,
                            metadata,
                            expectedWeightsJson,
                            now,
                            effectiveSyncedIds);
            if (candidate.latestBarTimestamp() != null
                    && (maxLatestBarTimestamp == null
                            || candidate.latestBarTimestamp().isAfter(maxLatestBarTimestamp))) {
                maxLatestBarTimestamp = candidate.latestBarTimestamp();
            }
            candidates.add(candidate.item());
        }

        List<ScreenerItemResponse> filtered =
                candidates.stream()
                        .filter(
                                item ->
                                        query.includeInsufficientData()
                                                || item.status() == ScoreStatus.READY)
                        .filter(
                                item ->
                                        query.minScore() == null
                                                || (item.finalScore() != null
                                                        && item.finalScore()
                                                                        .compareTo(query.minScore())
                                                                >= 0))
                        .filter(
                                item ->
                                        query.maxScore() == null
                                                || (item.finalScore() != null
                                                        && item.finalScore()
                                                                        .compareTo(query.maxScore())
                                                                <= 0))
                        .filter(
                                item ->
                                        query.rsiMin() == null
                                                || (item.latestRsi() != null
                                                        && item.latestRsi()
                                                                        .compareTo(query.rsiMin())
                                                                >= 0))
                        .filter(
                                item ->
                                        query.rsiMax() == null
                                                || (item.latestRsi() != null
                                                        && item.latestRsi()
                                                                        .compareTo(query.rsiMax())
                                                                <= 0))
                        .filter(
                                item ->
                                        trendFilter == null
                                                || item.trendDirection() == trendFilter)
                        .sorted(buildComparator(sortBy, ascending))
                        .toList();

        long totalElements = filtered.size();
        int page = query.page();
        int size = query.size();
        int totalPages =
                totalElements == 0 ? 0 : (int) ((totalElements + size - 1L) / size);
        int fromIndex = Math.min(page * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        List<ScreenerItemResponse> pageItems = filtered.subList(fromIndex, toIndex);

        Optional<ProviderSyncRun> latestSync =
                providerSyncRunRepository.findLatestByProvider(metadata.providerId());
        FreshnessMetadataResponse freshness =
                freshnessEvaluator.evaluate(metadata, maxLatestBarTimestamp, latestSync);

        return new ScreenerPageResponse(
                exchange.code(),
                interval.code(),
                false,
                scoringService.algorithmVersion(),
                page,
                size,
                totalElements,
                totalPages,
                SCREENER_DISCLAIMER,
                DataSourceMetadataResponse.from(metadata),
                freshness,
                pageItems);
    }

    public InstrumentSetupResponse analyzeInstrumentSetup(
            String rawSymbol, String rawInterval, Instant from, Instant to, boolean adjusted) {
        String normalizedSymbol = normalizeSymbol(rawSymbol);
        BarInterval interval = parseInterval(rawInterval);
        DateRange range = ingestionService.resolveAndValidateRange(interval, from, to);
        Instrument instrument = findInstrument(normalizedSymbol);

        ProviderMetadata metadata = marketDataProvider.getProviderMetadata();
        List<OhlcvBar> bars = loadBarsOrIngest(instrument, interval, range, metadata, adjusted);
        ScoreWeights weights = scoringProperties.toScoreWeights();

        TechnicalAnalysisResult indicatorResult =
                indicatorService.calculate(
                        bars,
                        Arrays.asList(IndicatorType.values()),
                        IndicatorConfiguration.defaults());
        TechnicalScoreResult scoreResult =
                scoringService.score(
                        instrument.symbol(),
                        bars,
                        indicatorResult,
                        weights,
                        range.to(),
                        scoringProperties.getMaxDataAgeHours(),
                        scoringProperties.isRejectStaleData(),
                        metadata.simulated());

        LatestQuote quote = to == null ? fetchLatestQuoteSafely(instrument.symbol()) : null;
        TechnicalSetupResult setup =
                setupService.analyze(
                        instrument.symbol(),
                        bars,
                        indicatorResult,
                        scoreResult,
                        quote,
                        metadata.simulated());

        Instant latestBarTimestamp = bars.isEmpty() ? null : bars.getLast().timestamp();
        Instant calculatedAt = clock.instant();
        Optional<ProviderSyncRun> latestSync =
                providerSyncRunRepository.findLatestByInstrumentAndInterval(
                        metadata.providerId(), instrument.id(), interval, adjusted);
        FreshnessMetadataResponse freshness =
                freshnessEvaluator.evaluate(metadata, latestBarTimestamp, latestSync);

        return InstrumentSetupResponse.from(
                instrument,
                interval.code(),
                adjusted,
                range.from(),
                range.to(),
                bars.size(),
                scoreResult.algorithmVersion(),
                scoreResult.indicatorEngineVersion(),
                setup,
                metadata.simulated(),
                calculatedAt,
                DataSourceMetadataResponse.from(metadata),
                freshness);
    }

    public OpportunitiesPageResponse listOpportunities(OpportunitiesQuery query) {
        validatePagination(query.page(), query.size());
        validateBounds("minMomentumScore", "minMomentumScore", query.minMomentumScore(), null);
        UniverseScope scope = parseUniverseScope(query.scope());
        SetupCategory categoryFilter = parseSetupCategory(query.category());
        String sortBy = parseOpportunitiesSortBy(query.sortBy());
        boolean ascending = parseSortAscending(query.sortDir());
        BarInterval interval = parseInterval(query.interval());

        Exchange exchange = resolveExchange(query.exchange());

        ProviderMetadata metadata = marketDataProvider.getProviderMetadata();
        ScoreWeights weights = scoringProperties.toScoreWeights();
        DateRange range = ingestionService.resolveAndValidateRange(interval, null, null);
        Set<UUID> initialSyncedIds =
                marketDataBarRepository.findSyncedInstrumentIds(
                        interval, metadata.providerId(), false, range.from(), range.to());
        List<Instrument> instruments =
                resolveTargetInstruments(exchange, scope, query.query(), initialSyncedIds);
        preIngestUnsyncedSafely(
                instruments,
                initialSyncedIds,
                interval,
                range,
                metadata,
                scope,
                query.query());

        Map<SetupCategory, Long> counts = new EnumMap<>(SetupCategory.class);
        for (SetupCategory c : SetupCategory.values()) {
            counts.put(c, 0L);
        }

        Instant maxLatestBarTimestamp = null;
        List<OpportunityItemResponse> allItems = new java.util.ArrayList<>(instruments.size());
        Map<String, List<OhlcvBar>> barsBySymbol = new HashMap<>(instruments.size());
        Instant calculatedAt = clock.instant();

        for (Instrument instrument : instruments) {
            List<OhlcvBar> bars =
                    marketDataBarRepository.findBars(
                            instrument.id(),
                            interval,
                            metadata.providerId(),
                            false,
                            range.from(),
                            range.to());
            barsBySymbol.put(instrument.symbol(), bars);
            if (!bars.isEmpty()) {
                Instant latestTs = bars.getLast().timestamp();
                if (maxLatestBarTimestamp == null || latestTs.isAfter(maxLatestBarTimestamp)) {
                    maxLatestBarTimestamp = latestTs;
                }
            }

            TechnicalAnalysisResult indicatorResult =
                    indicatorService.calculate(
                            bars,
                            Arrays.asList(IndicatorType.values()),
                            IndicatorConfiguration.defaults());
            TechnicalScoreResult scoreResult =
                    scoringService.score(
                            instrument.symbol(),
                            bars,
                            indicatorResult,
                            weights,
                            range.to(),
                            scoringProperties.getMaxDataAgeHours(),
                            scoringProperties.isRejectStaleData(),
                            metadata.simulated());

            TechnicalSetupResult setup =
                    setupService.analyze(
                            instrument.symbol(),
                            bars,
                            indicatorResult,
                            scoreResult,
                            null,
                            metadata.simulated());

            OpportunityItemResponse item =
                    OpportunityItemResponse.from(
                            instrument, setup, metadata.simulated(), calculatedAt);
            if (bars.isEmpty()) {
                List<String> reasons = new java.util.ArrayList<>(item.insufficientDataReasons().size() + 1);
                reasons.add(
                        "Veri henüz senkronize edilmedi; toplu senkronizasyon veya hisse detayı üzerinden çekilebilir.");
                reasons.addAll(item.insufficientDataReasons());
                item =
                        new OpportunityItemResponse(
                                item.symbol(),
                                item.name(),
                                item.exchange(),
                                item.sector(),
                                item.currency(),
                                item.status(),
                                item.category(),
                                item.staleData(),
                                item.simulated(),
                                item.technicalScore(),
                                item.momentumBreakoutScore(),
                                item.regime(),
                                item.trendDirection(),
                                item.technicalReferencePrice(),
                                item.latestRsi(),
                                item.volumeRatio(),
                                item.atr14(),
                                item.atrPercent(),
                                item.breakoutTriggerLevel(),
                                item.distanceToBreakoutPercent(),
                                item.breakoutConfirmed(),
                                item.supportWatchZone(),
                                item.resistanceExitZone(),
                                item.invalidationRiskLevel(),
                                item.invalidationRationale(),
                                item.riskReward(),
                                item.bistMargin(),
                                item.usedIndicators(),
                                item.rationaleItems(),
                                item.riskWarnings(),
                                List.copyOf(reasons),
                                item.headline(),
                                item.calculatedAt());
            }

            counts.merge(setup.category(), 1L, Long::sum);
            allItems.add(item);
        }

        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        for (SetupCategory c : SetupCategory.values()) {
            categoryCounts.put(c.name(), counts.get(c));
        }

        List<OpportunityItemResponse> filtered =
                allItems.stream()
                        .filter(
                                item ->
                                        query.includeInsufficientData()
                                                || item.status() == ScoreStatus.READY)
                        .filter(item -> categoryFilter == null || item.category() == categoryFilter)
                        .filter(
                                item ->
                                        query.minMomentumScore() == null
                                                || (item.momentumBreakoutScore() != null
                                                        && item.momentumBreakoutScore()
                                                                        .compareTo(
                                                                                query
                                                                                        .minMomentumScore())
                                                                >= 0))
                        .sorted(buildOpportunitiesComparator(sortBy, ascending))
                        .toList();

        long totalElements = filtered.size();
        int page = query.page();
        int size = query.size();
        int totalPages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1L) / size);
        int fromIndex = Math.min(page * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        List<OpportunityItemResponse> pageItems =
                enrichPageMarginsWithLiveQuotes(
                        filtered.subList(fromIndex, toIndex), barsBySymbol, interval);

        Optional<ProviderSyncRun> latestSync =
                providerSyncRunRepository.findLatestByProvider(metadata.providerId());
        FreshnessMetadataResponse freshness =
                freshnessEvaluator.evaluate(metadata, maxLatestBarTimestamp, latestSync);

        return new OpportunitiesPageResponse(
                exchange.code(),
                interval.code(),
                false,
                setupService.engineVersion(),
                scoringService.algorithmVersion(),
                page,
                size,
                totalElements,
                totalPages,
                categoryCounts,
                OPPORTUNITIES_DISCLAIMER,
                DataSourceMetadataResponse.from(metadata),
                freshness,
                pageItems);
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

    private List<Instrument> resolveTargetInstruments(
            Exchange exchange,
            UniverseScope scope,
            String rawQuery,
            Set<UUID> syncedIds) {
        if (rawQuery != null && !rawQuery.isBlank()) {
            return instrumentRepository.search(rawQuery.trim(), exchange.code(), 0, 30).items();
        }
        return switch (scope) {
            case BIST30 -> instrumentRepository.findTopActiveByExchangeCode(exchange.code(), 30);
            case BIST100 -> instrumentRepository.findTopActiveByExchangeCode(exchange.code(), 100);
            case SYNCED -> {
                List<Instrument> synced =
                        instrumentRepository.findActiveByExchangeCode(exchange.code()).stream()
                                .filter(i -> syncedIds.contains(i.id()))
                                .toList();
                yield synced.isEmpty()
                        ? instrumentRepository.findTopActiveByExchangeCode(exchange.code(), 30)
                        : synced;
            }
            case ALL -> instrumentRepository.findActiveByExchangeCode(exchange.code());
        };
    }

    private Set<UUID> preIngestUnsyncedSafely(
            List<Instrument> targets,
            Set<UUID> initialSyncedIds,
            BarInterval interval,
            DateRange range,
            ProviderMetadata metadata,
            UniverseScope scope,
            String rawQuery) {
        boolean hasSearchQuery = rawQuery != null && !rawQuery.isBlank();
        int maxOnDemandIngest;
        if (hasSearchQuery || scope == UniverseScope.BIST30) {
            maxOnDemandIngest = 30;
        } else if (scope == UniverseScope.BIST100) {
            maxOnDemandIngest = metadata.simulated() ? 100 : 40;
        } else {
            maxOnDemandIngest = metadata.simulated() ? 100 : 20;
        }

        List<Instrument> toIngest =
                targets.stream()
                        .filter(i -> !initialSyncedIds.contains(i.id()))
                        .limit(maxOnDemandIngest)
                        .toList();
        if (toIngest.isEmpty()) {
            return initialSyncedIds;
        }

        int threads = Math.min(5, toIngest.size());
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> futures = new java.util.ArrayList<>(toIngest.size());
            for (Instrument instrument : toIngest) {
                futures.add(
                        executor.submit(
                                () -> {
                                    try {
                                        ingestionService.ingestBars(
                                                instrument.symbol(),
                                                interval,
                                                range.from(),
                                                range.to(),
                                                false);
                                    } catch (AppException | MarketDataProviderException ignored) {
                                        // Isolate single-symbol provider failures cleanly.
                                    }
                                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ExecutionException ignored) {
                    // Already isolated inside runnable.
                }
            }
        }

        return marketDataBarRepository.findSyncedInstrumentIds(
                interval, metadata.providerId(), false, range.from(), range.to());
    }

    private List<OpportunityItemResponse> enrichPageMarginsWithLiveQuotes(
            List<OpportunityItemResponse> pageItems,
            Map<String, List<OhlcvBar>> barsBySymbol,
            BarInterval interval) {
        if (pageItems.isEmpty() || interval != BarInterval.ONE_DAY) {
            return pageItems;
        }
        List<OpportunityItemResponse> readyItems =
                pageItems.stream().filter(item -> item.status() == ScoreStatus.READY).toList();
        if (readyItems.isEmpty()) {
            return pageItems;
        }

        Map<String, LatestQuote> quotesBySymbol = new HashMap<>(readyItems.size());
        int threads = Math.min(5, readyItems.size());
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<LatestQuote>> futures = new java.util.ArrayList<>(readyItems.size());
            for (OpportunityItemResponse item : readyItems) {
                futures.add(executor.submit(() -> fetchLatestQuoteSafely(item.symbol())));
            }
            for (int i = 0; i < readyItems.size(); i++) {
                try {
                    LatestQuote quote = futures.get(i).get();
                    if (quote != null) {
                        quotesBySymbol.put(readyItems.get(i).symbol(), quote);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ExecutionException ignored) {
                    // Fallback cleanly to bar-derived BistMarginReference.
                }
            }
        }

        return pageItems.stream()
                .map(
                        item -> {
                            LatestQuote quote = quotesBySymbol.get(item.symbol());
                            if (quote == null) {
                                return item;
                            }
                            List<OhlcvBar> bars = barsBySymbol.getOrDefault(item.symbol(), List.of());
                            BistMarginReference updatedMargin =
                                    setupService.computeBistMargin(bars, quote);
                            if (updatedMargin == null) {
                                return item;
                            }
                            return new OpportunityItemResponse(
                                    item.symbol(),
                                    item.name(),
                                    item.exchange(),
                                    item.sector(),
                                    item.currency(),
                                    item.status(),
                                    item.category(),
                                    item.staleData(),
                                    item.simulated(),
                                    item.technicalScore(),
                                    item.momentumBreakoutScore(),
                                    item.regime(),
                                    item.trendDirection(),
                                    item.technicalReferencePrice(),
                                    item.latestRsi(),
                                    item.volumeRatio(),
                                    item.atr14(),
                                    item.atrPercent(),
                                    item.breakoutTriggerLevel(),
                                    item.distanceToBreakoutPercent(),
                                    item.breakoutConfirmed(),
                                    item.supportWatchZone(),
                                    item.resistanceExitZone(),
                                    item.invalidationRiskLevel(),
                                    item.invalidationRationale(),
                                    item.riskReward(),
                                    BistMarginReferenceResponse.from(updatedMargin),
                                    item.usedIndicators(),
                                    item.rationaleItems(),
                                    item.riskWarnings(),
                                    item.insufficientDataReasons(),
                                    item.headline(),
                                    item.calculatedAt());
                        })
                .toList();
    }

    private Instrument findInstrument(String normalizedSymbol) {
        return instrumentRepository
                .findBySymbol(normalizedSymbol)
                .orElseThrow(
                        () ->
                                new AppException(
                                        ErrorCode.NOT_FOUND,
                                        "Instrument not found: " + normalizedSymbol));
    }

    private List<OhlcvBar> loadBarsOrIngest(
            Instrument instrument,
            BarInterval interval,
            DateRange range,
            ProviderMetadata metadata,
            boolean adjusted) {
        List<OhlcvBar> bars =
                marketDataBarRepository.findBars(
                        instrument.id(),
                        interval,
                        metadata.providerId(),
                        adjusted,
                        range.from(),
                        range.to());
        if (bars.isEmpty()) {
            ingestionService.ingestBars(
                    instrument.symbol(), interval, range.from(), range.to(), adjusted);
            bars =
                    marketDataBarRepository.findBars(
                            instrument.id(),
                            interval,
                            metadata.providerId(),
                            adjusted,
                            range.from(),
                            range.to());
        }
        return bars;
    }

    private LatestQuote fetchLatestQuoteSafely(String symbol) {
        try {
            return marketDataProvider.getLatestQuote(symbol);
        } catch (MarketDataProviderException ex) {
            return null;
        }
    }

    private ScreenerCandidate resolveCandidate(
            Instrument instrument,
            BarInterval interval,
            ProviderMetadata metadata,
            String expectedWeightsJson,
            Instant now,
            Set<UUID> syncedIds) {
        Optional<TechnicalScoreRun> existingRun =
                scoreRunRepository.findLatestMatching(
                        instrument.id(),
                        interval,
                        metadata.providerId(),
                        false,
                        scoringService.algorithmVersion());
        Optional<OhlcvBar> latestBar =
                marketDataBarRepository.findLatestBar(
                        instrument.id(), interval, metadata.providerId(), false);

        if (existingRun.isPresent() && isReusableRun(existingRun.get(), latestBar, expectedWeightsJson, now)) {
            TechnicalScoreRun run = existingRun.get();
            ScoreExplanation explanation = deserializeExplanation(run.explanationJson());
            ScreenerItemResponse item =
                    new ScreenerItemResponse(
                            run.id(),
                            instrument.symbol(),
                            instrument.name(),
                            instrument.exchangeCode(),
                            instrument.sector(),
                            instrument.currency(),
                            run.status(),
                            run.finalScore(),
                            run.regime(),
                            run.trendDirection(),
                            run.latestClose(),
                            run.latestRsi(),
                            run.simulated(),
                            run.staleData(),
                            explanation.headline(),
                            run.calculatedAt());
            return new ScreenerCandidate(item, run.latestBarTimestamp());
        }

        if (!syncedIds.contains(instrument.id())) {
            ScreenerItemResponse item =
                    new ScreenerItemResponse(
                            UUID.randomUUID(),
                            instrument.symbol(),
                            instrument.name(),
                            instrument.exchangeCode(),
                            instrument.sector(),
                            instrument.currency(),
                            ScoreStatus.INSUFFICIENT_DATA,
                            null,
                            ScoreRegime.INSUFFICIENT_DATA,
                            TrendDirection.UNKNOWN,
                            null,
                            null,
                            metadata.simulated(),
                            false,
                            instrument.symbol()
                                    + ": Veri henüz senkronize edilmedi (INSUFFICIENT_DATA).",
                            now);
            return new ScreenerCandidate(item, null);
        }

        InstrumentAnalysisResponse analysis =
                analyzeInstrument(instrument.symbol(), interval.code(), null, null, false);
        ScreenerItemResponse item =
                new ScreenerItemResponse(
                        analysis.scoreRunId(),
                        instrument.symbol(),
                        instrument.name(),
                        instrument.exchangeCode(),
                        instrument.sector(),
                        instrument.currency(),
                        analysis.status(),
                        analysis.finalScore(),
                        analysis.regime(),
                        analysis.trendDirection(),
                        analysis.latestClose(),
                        analysis.latestRsi(),
                        analysis.dataSource().simulated(),
                        analysis.staleData(),
                        analysis.explanation().headline(),
                        analysis.calculatedAt());
        return new ScreenerCandidate(item, analysis.freshness().latestDataTimestamp());
    }

    private boolean isReusableRun(
            TechnicalScoreRun run,
            Optional<OhlcvBar> latestBar,
            String expectedWeightsJson,
            Instant now) {
        if (run.staleData() || run.latestBarTimestamp() == null || latestBar.isEmpty()) {
            return false;
        }
        if (latestBar.get().timestamp().isAfter(run.latestBarTimestamp())) {
            return false;
        }
        if (!expectedWeightsJson.equals(run.weightsJson())) {
            return false;
        }
        return Duration.between(run.latestBarTimestamp(), now)
                        .compareTo(Duration.ofHours(scoringProperties.getMaxDataAgeHours()))
                <= 0;
    }

    private static Comparator<ScreenerItemResponse> buildComparator(
            String sortBy, boolean ascending) {
        Comparator<ScreenerItemResponse> primary =
                switch (sortBy) {
                    case "rsi" -> Comparator.comparing(
                            ScreenerItemResponse::latestRsi,
                            ascending
                                    ? Comparator.nullsLast(Comparator.naturalOrder())
                                    : Comparator.nullsLast(Comparator.reverseOrder()));
                    case "symbol" -> Comparator.comparing(
                            ScreenerItemResponse::symbol,
                            ascending ? Comparator.naturalOrder() : Comparator.reverseOrder());
                    default -> Comparator.comparing(
                            ScreenerItemResponse::finalScore,
                            ascending
                                    ? Comparator.nullsLast(Comparator.naturalOrder())
                                    : Comparator.nullsLast(Comparator.reverseOrder()));
                };
        return primary.thenComparing(ScreenerItemResponse::symbol);
    }

    private static Comparator<OpportunityItemResponse> buildOpportunitiesComparator(
            String sortBy, boolean ascending) {
        Comparator<BigDecimal> decimalOrder =
                ascending
                        ? Comparator.nullsLast(Comparator.naturalOrder())
                        : Comparator.nullsLast(Comparator.reverseOrder());
        Comparator<OpportunityItemResponse> primary =
                switch (sortBy) {
                    case "score" ->
                            Comparator.comparing(
                                    OpportunityItemResponse::technicalScore, decimalOrder);
                    case "volumeratio" ->
                            Comparator.comparing(
                                    OpportunityItemResponse::volumeRatio, decimalOrder);
                    case "ceilingdistance" ->
                            Comparator.comparing(
                                    item ->
                                            item.bistMargin() == null
                                                    ? null
                                                    : item.bistMargin().distanceToCeilingPercent(),
                                    decimalOrder);
                    case "riskreward" ->
                            Comparator.comparing(
                                    item ->
                                            item.riskReward() == null
                                                    ? null
                                                    : item.riskReward().riskRewardToR2(),
                                    decimalOrder);
                    case "symbol" ->
                            Comparator.comparing(
                                    OpportunityItemResponse::symbol,
                                    ascending
                                            ? Comparator.naturalOrder()
                                            : Comparator.reverseOrder());
                    default ->
                            Comparator.comparing(
                                    OpportunityItemResponse::momentumBreakoutScore, decimalOrder);
                };
        return primary.thenComparing(OpportunityItemResponse::symbol);
    }

    private static SetupCategory parseSetupCategory(String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return null;
        }
        try {
            return SetupCategory.fromCode(rawCategory);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
    }

    private static UniverseScope parseUniverseScope(String rawScope) {
        try {
            return UniverseScope.fromCode(rawScope);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
    }

    private static String parseOpportunitiesSortBy(String rawSortBy) {
        if (rawSortBy == null || rawSortBy.isBlank()) {
            return "momentum";
        }
        String normalized = rawSortBy.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("momentum")
                && !normalized.equals("score")
                && !normalized.equals("volumeratio")
                && !normalized.equals("ceilingdistance")
                && !normalized.equals("riskreward")
                && !normalized.equals("symbol")) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Unsupported sortBy '"
                            + rawSortBy
                            + "'. Supported: momentum, score, volumeRatio, ceilingDistance, riskReward, symbol");
        }
        return normalized;
    }

    private static Map<String, BigDecimal> weightsToStringMap(ScoreWeights weights) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (ScoreComponentType type : ScoreComponentType.values()) {
            map.put(type.name(), weights.weightOf(type));
        }
        return map;
    }

    private String serializeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize scoring payload", ex);
        }
    }

    private ScoreExplanation deserializeExplanation(String json) {
        try {
            return objectMapper.readValue(json, ScoreExplanation.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize persisted score explanation", ex);
        }
    }

    private static void validatePagination(int page, int size) {
        if (page < 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "page must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR, "size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private static void validateBounds(
            String minName, String maxName, BigDecimal min, BigDecimal max) {
        if (min != null && (min.compareTo(BigDecimal.ZERO) < 0 || min.compareTo(HUNDRED) > 0)) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR, minName + " must be between 0 and 100");
        }
        if (max != null && (max.compareTo(BigDecimal.ZERO) < 0 || max.compareTo(HUNDRED) > 0)) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR, maxName + " must be between 0 and 100");
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR, minName + " must not exceed " + maxName);
        }
    }

    private static TrendDirection parseTrendFilter(String rawTrend) {
        if (rawTrend == null || rawTrend.isBlank()) {
            return null;
        }
        try {
            return TrendDirection.fromCode(rawTrend);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
    }

    private static String parseSortBy(String rawSortBy) {
        if (rawSortBy == null || rawSortBy.isBlank()) {
            return "score";
        }
        String normalized = rawSortBy.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("score")
                && !normalized.equals("rsi")
                && !normalized.equals("symbol")) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Unsupported sortBy '" + rawSortBy + "'. Supported: score, rsi, symbol");
        }
        return normalized;
    }

    private static boolean parseSortAscending(String rawSortDir) {
        if (rawSortDir == null || rawSortDir.isBlank()) {
            return false;
        }
        String normalized = rawSortDir.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("asc")) {
            return true;
        }
        if (normalized.equals("desc")) {
            return false;
        }
        throw new AppException(
                ErrorCode.VALIDATION_ERROR,
                "Unsupported sortDir '" + rawSortDir + "'. Supported: asc, desc");
    }

    private static String normalizeSymbol(String rawSymbol) {
        try {
            return SymbolNormalizer.normalize(rawSymbol);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
    }

    private static BarInterval parseInterval(String rawInterval) {
        try {
            return BarInterval.fromCode(rawInterval);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
    }

    private record ScreenerCandidate(ScreenerItemResponse item, Instant latestBarTimestamp) {
    }
}
