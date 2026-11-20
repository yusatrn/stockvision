package com.stockvision.stock.application;

import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.market.domain.Exchange;
import com.stockvision.market.domain.ExchangeRepository;
import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import com.stockvision.marketdata.api.FreshnessMetadataResponse;
import com.stockvision.marketdata.application.FreshnessEvaluator;
import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.marketdata.domain.LatestQuote;
import com.stockvision.marketdata.domain.MarketDataProvider;
import com.stockvision.marketdata.domain.MarketDataProviderException;
import com.stockvision.marketdata.domain.ProviderMetadata;
import com.stockvision.marketdata.domain.ProviderSyncRun;
import com.stockvision.marketdata.domain.ProviderSyncRunRepository;
import com.stockvision.stock.api.InstrumentDetailResponse;
import com.stockvision.stock.api.InstrumentPageResponse;
import com.stockvision.stock.api.InstrumentSummaryResponse;
import com.stockvision.stock.api.LatestQuoteResponse;
import com.stockvision.stock.domain.Instrument;
import com.stockvision.stock.domain.InstrumentPage;
import com.stockvision.stock.domain.InstrumentRepository;
import com.stockvision.stock.domain.SymbolNormalizer;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for searching instruments and retrieving instrument details with quotes.
 */
@Service
@Transactional(readOnly = true)
public class InstrumentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final InstrumentRepository instrumentRepository;
    private final ExchangeRepository exchangeRepository;
    private final MarketDataProvider marketDataProvider;
    private final ProviderSyncRunRepository providerSyncRunRepository;
    private final FreshnessEvaluator freshnessEvaluator;

    public InstrumentService(
            InstrumentRepository instrumentRepository,
            ExchangeRepository exchangeRepository,
            MarketDataProvider marketDataProvider,
            ProviderSyncRunRepository providerSyncRunRepository,
            FreshnessEvaluator freshnessEvaluator) {
        this.instrumentRepository = instrumentRepository;
        this.exchangeRepository = exchangeRepository;
        this.marketDataProvider = marketDataProvider;
        this.providerSyncRunRepository = providerSyncRunRepository;
        this.freshnessEvaluator = freshnessEvaluator;
    }

    public InstrumentPageResponse search(String query, String exchangeCode, int page, int size) {
        if (page < 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "page must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR, "size must be between 1 and " + MAX_PAGE_SIZE);
        }

        InstrumentPage resultPage = instrumentRepository.search(query, exchangeCode, page, size);
        ProviderMetadata metadata = marketDataProvider.getProviderMetadata();

        return new InstrumentPageResponse(
                resultPage.items().stream().map(InstrumentSummaryResponse::from).toList(),
                resultPage.page(),
                resultPage.size(),
                resultPage.totalElements(),
                resultPage.totalPages(),
                DataSourceMetadataResponse.from(metadata));
    }

    public InstrumentDetailResponse getDetail(String rawSymbol) {
        String normalizedSymbol;
        try {
            normalizedSymbol = SymbolNormalizer.normalize(rawSymbol);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }

        Instrument instrument =
                instrumentRepository
                        .findBySymbol(normalizedSymbol)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.NOT_FOUND,
                                                "Instrument not found: " + normalizedSymbol));

        Exchange exchange =
                exchangeRepository
                        .findById(instrument.exchangeId())
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.NOT_FOUND,
                                                "Exchange not found for instrument: "
                                                        + normalizedSymbol));

        ProviderMetadata metadata = marketDataProvider.getProviderMetadata();
        LatestQuote quote;
        try {
            quote = marketDataProvider.getLatestQuote(instrument.symbol());
        } catch (MarketDataProviderException | IllegalStateException ex) {
            throw new AppException(
                    ErrorCode.PROVIDER_UNAVAILABLE,
                    "Market data provider failed to supply latest quote",
                    ex);
        }

        Optional<ProviderSyncRun> latestSync =
                providerSyncRunRepository.findLatestByInstrumentAndInterval(
                        metadata.providerId(), instrument.id(), BarInterval.ONE_DAY, false);

        FreshnessMetadataResponse freshness =
                freshnessEvaluator.evaluate(metadata, quote.quotedAt(), latestSync);

        return new InstrumentDetailResponse(
                instrument.id(),
                instrument.symbol(),
                instrument.name(),
                exchange.code(),
                exchange.name(),
                exchange.timezone(),
                instrument.sector(),
                instrument.currency(),
                instrument.type(),
                instrument.active(),
                LatestQuoteResponse.from(quote),
                DataSourceMetadataResponse.from(metadata),
                freshness);
    }
}
