package com.stockvision.market.application;

import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.market.api.ExchangeResponse;
import com.stockvision.market.api.MarketMoverResponse;
import com.stockvision.market.api.MarketSummaryResponse;
import com.stockvision.market.domain.Exchange;
import com.stockvision.market.domain.ExchangeRepository;
import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import com.stockvision.marketdata.api.FreshnessMetadataResponse;
import com.stockvision.marketdata.application.FreshnessEvaluator;
import com.stockvision.marketdata.domain.LatestQuote;
import com.stockvision.marketdata.domain.MarketDataProvider;
import com.stockvision.marketdata.domain.MarketDataProviderException;
import com.stockvision.marketdata.domain.ProviderMetadata;
import com.stockvision.marketdata.domain.ProviderSyncRun;
import com.stockvision.marketdata.domain.ProviderSyncRunRepository;
import com.stockvision.stock.domain.Instrument;
import com.stockvision.stock.domain.InstrumentRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for exchange listings and market summary overviews.
 */
@Service
@Transactional(readOnly = true)
public class MarketService {

    private static final int MAX_SUMMARY_MOVERS = 10;

    private final ExchangeRepository exchangeRepository;
    private final InstrumentRepository instrumentRepository;
    private final MarketDataProvider marketDataProvider;
    private final ProviderSyncRunRepository providerSyncRunRepository;
    private final FreshnessEvaluator freshnessEvaluator;

    public MarketService(
            ExchangeRepository exchangeRepository,
            InstrumentRepository instrumentRepository,
            MarketDataProvider marketDataProvider,
            ProviderSyncRunRepository providerSyncRunRepository,
            FreshnessEvaluator freshnessEvaluator) {
        this.exchangeRepository = exchangeRepository;
        this.instrumentRepository = instrumentRepository;
        this.marketDataProvider = marketDataProvider;
        this.providerSyncRunRepository = providerSyncRunRepository;
        this.freshnessEvaluator = freshnessEvaluator;
    }

    public List<ExchangeResponse> listMarkets() {
        return exchangeRepository.findAllActive().stream().map(ExchangeResponse::from).toList();
    }

    public MarketSummaryResponse getSummary(String rawExchangeCode) {
        if (rawExchangeCode == null || rawExchangeCode.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "exchange code must not be blank");
        }
        String exchangeCode = rawExchangeCode.trim().toUpperCase(Locale.ROOT);
        Exchange exchange =
                exchangeRepository
                        .findByCode(exchangeCode)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.NOT_FOUND,
                                                "Exchange not found: " + exchangeCode));

        long activeCount = instrumentRepository.countActiveByExchangeCode(exchange.code());
        List<Instrument> topInstruments =
                instrumentRepository.findTopActiveByExchangeCode(exchange.code(), 20);

        ProviderMetadata metadata = marketDataProvider.getProviderMetadata();
        List<MarketMoverResponse> collected = new java.util.ArrayList<>(MAX_SUMMARY_MOVERS);
        MarketDataProviderException lastFailure = null;
        for (Instrument instrument : topInstruments) {
            if (collected.size() >= MAX_SUMMARY_MOVERS) {
                break;
            }
            try {
                LatestQuote quote = marketDataProvider.getLatestQuote(instrument.symbol());
                collected.add(
                        new MarketMoverResponse(
                                instrument.symbol(),
                                instrument.name(),
                                quote.price(),
                                quote.changeAmount(),
                                quote.changePercent(),
                                quote.volume(),
                                quote.currency(),
                                quote.quotedAt(),
                                quote.simulated()));
            } catch (MarketDataProviderException ex) {
                lastFailure = ex;
            } catch (IllegalStateException ex) {
                if (lastFailure == null) {
                    lastFailure = new MarketDataProviderException(ex.getMessage(), ex);
                }
            }
        }
        if (activeCount > 0 && !topInstruments.isEmpty() && collected.isEmpty()) {
            throw new AppException(
                    ErrorCode.PROVIDER_UNAVAILABLE,
                    "Market data provider failed to supply market summary quotes",
                    lastFailure);
        }

        List<MarketMoverResponse> movers =
                collected.stream()
                        .sorted(
                                Comparator.comparing(
                                                (MarketMoverResponse m) ->
                                                        m.changePercent().abs())
                                        .reversed()
                                        .thenComparing(MarketMoverResponse::symbol))
                        .toList();

        Instant latestQuotedAt =
                movers.stream()
                        .map(MarketMoverResponse::quotedAt)
                        .max(Instant::compareTo)
                        .orElse(null);

        Optional<ProviderSyncRun> latestSync =
                providerSyncRunRepository.findLatestByProvider(metadata.providerId());

        FreshnessMetadataResponse freshness =
                freshnessEvaluator.evaluate(metadata, latestQuotedAt, latestSync);

        String summaryNote =
                metadata.simulated()
                        ? "Piyasa özeti yalnızca simüle mock fiyatlardan türetilmiştir; canlı borsa verisi değildir."
                        : "Piyasa özeti kayıtlı ve veri sağlayıcısından alınan güncel/gecikmeli fiyatları yansıtır.";

        return new MarketSummaryResponse(
                ExchangeResponse.from(exchange),
                activeCount,
                metadata.simulated(),
                summaryNote,
                DataSourceMetadataResponse.from(metadata),
                freshness,
                movers);
    }
}
