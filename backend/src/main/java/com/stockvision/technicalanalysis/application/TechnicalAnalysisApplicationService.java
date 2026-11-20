package com.stockvision.technicalanalysis.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import com.stockvision.marketdata.api.FreshnessMetadataResponse;
import com.stockvision.marketdata.application.FreshnessEvaluator;
import com.stockvision.marketdata.application.MarketDataIngestionService;
import com.stockvision.marketdata.application.MarketDataIngestionService.DateRange;
import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.marketdata.domain.MarketDataBarRepository;
import com.stockvision.marketdata.domain.MarketDataProvider;
import com.stockvision.marketdata.domain.OhlcvBar;
import com.stockvision.marketdata.domain.ProviderMetadata;
import com.stockvision.marketdata.domain.ProviderSyncRun;
import com.stockvision.marketdata.domain.ProviderSyncRunRepository;
import com.stockvision.stock.domain.Instrument;
import com.stockvision.stock.domain.InstrumentRepository;
import com.stockvision.stock.domain.SymbolNormalizer;
import com.stockvision.technicalanalysis.api.IndicatorSeriesResponse;
import com.stockvision.technicalanalysis.api.TechnicalIndicatorsResponse;
import com.stockvision.technicalanalysis.domain.AnalysisRun;
import com.stockvision.technicalanalysis.domain.AnalysisRunRepository;
import com.stockvision.technicalanalysis.domain.AnalysisRunType;
import com.stockvision.technicalanalysis.domain.IndicatorConfiguration;
import com.stockvision.technicalanalysis.domain.IndicatorType;
import com.stockvision.technicalanalysis.domain.TechnicalAnalysisResult;
import com.stockvision.technicalanalysis.domain.TechnicalIndicatorService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Orchestrates historical bar loading (with on-demand ingestion when missing), deterministic
 * technical indicator computation, and {@link AnalysisRun} persistence.
 */
@Service
public class TechnicalAnalysisApplicationService {

    private static final String DISCLAIMER =
            "Teknik göstergeler geçmiş veya simüle fiyat ve hacim verilerinin betimsel matematiksel özetleridir; "
                    + "gelecekteki getirileri öngörmez ve yatırım danışmanlığı niteliği taşımaz.";

    private final InstrumentRepository instrumentRepository;
    private final MarketDataProvider marketDataProvider;
    private final MarketDataBarRepository marketDataBarRepository;
    private final ProviderSyncRunRepository providerSyncRunRepository;
    private final MarketDataIngestionService ingestionService;
    private final FreshnessEvaluator freshnessEvaluator;
    private final TechnicalIndicatorService technicalIndicatorService;
    private final AnalysisRunRepository analysisRunRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TechnicalAnalysisApplicationService(
            InstrumentRepository instrumentRepository,
            MarketDataProvider marketDataProvider,
            MarketDataBarRepository marketDataBarRepository,
            ProviderSyncRunRepository providerSyncRunRepository,
            MarketDataIngestionService ingestionService,
            FreshnessEvaluator freshnessEvaluator,
            TechnicalIndicatorService technicalIndicatorService,
            AnalysisRunRepository analysisRunRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.instrumentRepository = instrumentRepository;
        this.marketDataProvider = marketDataProvider;
        this.marketDataBarRepository = marketDataBarRepository;
        this.providerSyncRunRepository = providerSyncRunRepository;
        this.ingestionService = ingestionService;
        this.freshnessEvaluator = freshnessEvaluator;
        this.technicalIndicatorService = technicalIndicatorService;
        this.analysisRunRepository = analysisRunRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public TechnicalIndicatorsResponse calculateIndicators(TechnicalIndicatorsQuery query) {
        String normalizedSymbol = normalizeSymbol(query.symbol());
        BarInterval interval = parseInterval(query.interval());
        List<IndicatorType> requestedIndicators = parseIndicators(query.indicators());
        IndicatorConfiguration configuration =
                query.configuration() != null
                        ? query.configuration()
                        : IndicatorConfiguration.defaults();
        DateRange range = ingestionService.resolveAndValidateRange(interval, query.from(), query.to());

        Instrument instrument =
                instrumentRepository
                        .findBySymbol(normalizedSymbol)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.NOT_FOUND,
                                                "Instrument not found: " + normalizedSymbol));

        ProviderMetadata metadata = marketDataProvider.getProviderMetadata();
        List<OhlcvBar> bars =
                marketDataBarRepository.findBars(
                        instrument.id(),
                        interval,
                        metadata.providerId(),
                        query.adjusted(),
                        range.from(),
                        range.to());

        if (bars.isEmpty()) {
            ingestionService.ingestBars(
                    instrument.symbol(), interval, range.from(), range.to(), query.adjusted());
            bars =
                    marketDataBarRepository.findBars(
                            instrument.id(),
                            interval,
                            metadata.providerId(),
                            query.adjusted(),
                            range.from(),
                            range.to());
        }

        TechnicalAnalysisResult result =
                technicalIndicatorService.calculate(bars, requestedIndicators, configuration);

        Instant latestBarTimestamp = bars.isEmpty() ? null : bars.getLast().timestamp();
        Instant calculatedAt = clock.instant();
        String parametersJson =
                serializeParameters(configuration, new LinkedHashSet<>(requestedIndicators));

        AnalysisRun savedRun =
                analysisRunRepository.save(
                        new AnalysisRun(
                                UUID.randomUUID(),
                                instrument.id(),
                                instrument.symbol(),
                                interval,
                                metadata.providerId(),
                                query.adjusted(),
                                metadata.simulated(),
                                AnalysisRunType.TECHNICAL_INDICATORS,
                                result.engineVersion(),
                                result.status(),
                                parametersJson,
                                result.barCount(),
                                range.from(),
                                range.to(),
                                latestBarTimestamp,
                                calculatedAt));

        Optional<ProviderSyncRun> latestSync =
                providerSyncRunRepository.findLatestByInstrumentAndInterval(
                        metadata.providerId(), instrument.id(), interval, query.adjusted());
        FreshnessMetadataResponse freshness =
                freshnessEvaluator.evaluate(metadata, latestBarTimestamp, latestSync);

        return new TechnicalIndicatorsResponse(
                savedRun.id(),
                instrument.symbol(),
                instrument.exchangeCode(),
                interval.code(),
                query.adjusted(),
                range.from(),
                range.to(),
                result.barCount(),
                result.engineVersion(),
                result.status(),
                calculatedAt,
                DISCLAIMER,
                DataSourceMetadataResponse.from(metadata),
                freshness,
                result.series().stream().map(IndicatorSeriesResponse::from).toList());
    }

    private String serializeParameters(
            IndicatorConfiguration configuration, Set<IndicatorType> indicators) {
        try {
            return objectMapper.writeValueAsString(configuration.toParameterMap(indicators));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize indicator configuration", ex);
        }
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

    private static List<IndicatorType> parseIndicators(List<String> rawIndicators) {
        if (rawIndicators == null || rawIndicators.isEmpty()) {
            return Arrays.asList(IndicatorType.values());
        }
        Set<IndicatorType> deduplicated = new LinkedHashSet<>();
        for (String item : rawIndicators) {
            if (item == null || item.isBlank()) {
                continue;
            }
            try {
                deduplicated.add(IndicatorType.fromCode(item));
            } catch (IllegalArgumentException ex) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        }
        if (deduplicated.isEmpty()) {
            return Arrays.asList(IndicatorType.values());
        }
        return new ArrayList<>(deduplicated);
    }
}
