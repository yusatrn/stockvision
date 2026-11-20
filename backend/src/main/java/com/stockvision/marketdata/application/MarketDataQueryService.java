package com.stockvision.marketdata.application;

import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import com.stockvision.marketdata.api.FreshnessMetadataResponse;
import com.stockvision.marketdata.api.HistoricalBarsResponse;
import com.stockvision.marketdata.api.OhlcvBarResponse;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Serves historical OHLCV bars with on-demand idempotent ingestion and explicit freshness/source
 * metadata.
 */
@Service
public class MarketDataQueryService {

    private static final String MISSING_DATA_NOTE =
            "Hafta sonları ve borsa tatilleri açıkça hariç tutulur; hiçbir zaman ileri doldurulmaz (forward-fill yapılmaz). "
                    + "Düzeltilmiş (adjusted) ve düzeltilmemiş seriler ayrı saklanır.";

    private final InstrumentRepository instrumentRepository;
    private final MarketDataProvider marketDataProvider;
    private final MarketDataBarRepository marketDataBarRepository;
    private final ProviderSyncRunRepository providerSyncRunRepository;
    private final MarketDataIngestionService ingestionService;
    private final FreshnessEvaluator freshnessEvaluator;

    public MarketDataQueryService(
            InstrumentRepository instrumentRepository,
            MarketDataProvider marketDataProvider,
            MarketDataBarRepository marketDataBarRepository,
            ProviderSyncRunRepository providerSyncRunRepository,
            MarketDataIngestionService ingestionService,
            FreshnessEvaluator freshnessEvaluator) {
        this.instrumentRepository = instrumentRepository;
        this.marketDataProvider = marketDataProvider;
        this.marketDataBarRepository = marketDataBarRepository;
        this.providerSyncRunRepository = providerSyncRunRepository;
        this.ingestionService = ingestionService;
        this.freshnessEvaluator = freshnessEvaluator;
    }

    public HistoricalBarsResponse getHistoricalBars(
            String rawSymbol,
            String rawInterval,
            Instant from,
            Instant to,
            boolean adjusted) {
        String normalizedSymbol = normalizeSymbol(rawSymbol);
        BarInterval interval = parseInterval(rawInterval);
        DateRange range = ingestionService.resolveAndValidateRange(interval, from, to);

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

        Instant latestBarTimestamp =
                bars.isEmpty()
                        ? marketDataBarRepository
                                .findLatestBar(
                                        instrument.id(),
                                        interval,
                                        metadata.providerId(),
                                        adjusted)
                                .map(OhlcvBar::timestamp)
                                .orElse(null)
                        : bars.get(bars.size() - 1).timestamp();

        Optional<ProviderSyncRun> latestSync =
                providerSyncRunRepository.findLatestByInstrumentAndInterval(
                        metadata.providerId(), instrument.id(), interval, adjusted);

        FreshnessMetadataResponse freshness =
                freshnessEvaluator.evaluate(metadata, latestBarTimestamp, latestSync);

        return new HistoricalBarsResponse(
                instrument.symbol(),
                instrument.exchangeCode(),
                interval.code(),
                adjusted,
                range.from(),
                range.to(),
                bars.size(),
                MISSING_DATA_NOTE,
                DataSourceMetadataResponse.from(metadata),
                freshness,
                bars.stream().map(OhlcvBarResponse::from).toList());
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
}
