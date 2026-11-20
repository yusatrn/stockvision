package com.stockvision.portfolio.application;

import com.stockvision.common.domain.FinancialMath;
import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import com.stockvision.marketdata.api.FreshnessMetadataResponse;
import com.stockvision.marketdata.application.FreshnessEvaluator;
import com.stockvision.marketdata.domain.LatestQuote;
import com.stockvision.marketdata.domain.MarketDataProvider;
import com.stockvision.marketdata.domain.MarketDataProviderException;
import com.stockvision.marketdata.domain.ProviderMetadata;
import com.stockvision.portfolio.api.CreatePortfolioRequest;
import com.stockvision.portfolio.api.CreateTransactionRequest;
import com.stockvision.portfolio.api.PortfolioHoldingResponse;
import com.stockvision.portfolio.api.PortfolioPerformanceResponse;
import com.stockvision.portfolio.api.PortfolioSummaryResponse;
import com.stockvision.portfolio.api.PortfolioTransactionResponse;
import com.stockvision.portfolio.domain.DerivedPosition;
import com.stockvision.portfolio.domain.FeeTaxPolicy;
import com.stockvision.portfolio.domain.LedgerCalculationResult;
import com.stockvision.portfolio.domain.Portfolio;
import com.stockvision.portfolio.domain.PortfolioCalculator;
import com.stockvision.portfolio.domain.PortfolioRepository;
import com.stockvision.portfolio.domain.PortfolioTransaction;
import com.stockvision.portfolio.domain.PortfolioValuationStatus;
import com.stockvision.stock.domain.Instrument;
import com.stockvision.stock.domain.InstrumentRepository;
import com.stockvision.stock.domain.SymbolNormalizer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * Application service for user-owned virtual portfolios, chronological BUY/SELL ledger entry,
 * and derived BigDecimal performance calculation.
 */
@Service
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final String DISCLAIMER =
            "Sanal portföy defteri ve kâr/zarar hesaplamaları yalnızca piyasa analizi ve eğitimsel karar destek amaçlıdır; "
                    + "hiçbir aracı kuruma bağlı değildir ve kâr garantisi içermez.";

    private final PortfolioRepository portfolioRepository;
    private final InstrumentRepository instrumentRepository;
    private final MarketDataProvider marketDataProvider;
    private final FreshnessEvaluator freshnessEvaluator;
    private final Clock clock;

    public PortfolioService(
            PortfolioRepository portfolioRepository,
            InstrumentRepository instrumentRepository,
            MarketDataProvider marketDataProvider,
            FreshnessEvaluator freshnessEvaluator,
            Clock clock) {
        this.portfolioRepository = portfolioRepository;
        this.instrumentRepository = instrumentRepository;
        this.marketDataProvider = marketDataProvider;
        this.freshnessEvaluator = freshnessEvaluator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PortfolioSummaryResponse> listPortfolios(UUID userId) {
        List<Portfolio> portfolios = portfolioRepository.findAllByUserId(userId);
        List<UUID> portfolioIds = portfolios.stream().map(Portfolio::id).toList();
        Map<UUID, List<PortfolioTransaction>> txByPortfolio =
                portfolioRepository.findTransactionsByPortfolioIds(portfolioIds).stream()
                        .collect(Collectors.groupingBy(PortfolioTransaction::portfolioId));

        return portfolios.stream()
                .map(
                        portfolio -> {
                            List<PortfolioTransaction> txs =
                                    txByPortfolio.getOrDefault(portfolio.id(), List.of());
                            LedgerCalculationResult ledger =
                                    PortfolioCalculator.calculate(portfolio.feeTaxPolicy(), txs);
                            int openCount =
                                    (int)
                                            ledger.positions().values().stream()
                                                    .filter(
                                                            pos ->
                                                                    pos.openQuantity()
                                                                                    .compareTo(
                                                                                            BigDecimal
                                                                                                    .ZERO)
                                                                            > 0)
                                                    .count();
                            return new PortfolioSummaryResponse(
                                    portfolio.id(),
                                    portfolio.name(),
                                    portfolio.description(),
                                    portfolio.baseCurrency(),
                                    portfolio.feeTaxPolicy(),
                                    openCount,
                                    txs.size(),
                                    portfolio.createdAt(),
                                    portfolio.updatedAt());
                        })
                .toList();
    }

    @Transactional
    public PortfolioSummaryResponse createPortfolio(UUID userId, CreatePortfolioRequest request) {
        String name = normalizeRequiredText(request.name(), "Portfolio name is required");
        String description = normalizeOptionalText(request.description());
        String baseCurrency = normalizeCurrency(request.baseCurrency());
        FeeTaxPolicy feeTaxPolicy =
                request.feeTaxPolicy() != null ? request.feeTaxPolicy() : FeeTaxPolicy.EXCLUDED_MVP;

        if (portfolioRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new AppException(
                    ErrorCode.CONFLICT, "Portfolio with name '" + name + "' already exists");
        }

        Instant now = clock.instant();
        Portfolio portfolio =
                new Portfolio(
                        UUID.randomUUID(),
                        userId,
                        name,
                        description,
                        baseCurrency,
                        feeTaxPolicy,
                        now,
                        now);
        try {
            Portfolio saved = portfolioRepository.save(portfolio);
            return new PortfolioSummaryResponse(
                    saved.id(),
                    saved.name(),
                    saved.description(),
                    saved.baseCurrency(),
                    saved.feeTaxPolicy(),
                    0,
                    0,
                    saved.createdAt(),
                    saved.updatedAt());
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(
                    ErrorCode.CONFLICT, "Portfolio with name '" + name + "' already exists", ex);
        }
    }

    @Transactional
    public void deletePortfolio(UUID userId, UUID portfolioId) {
        Portfolio existing = requireOwnedPortfolio(portfolioId, userId);
        portfolioRepository.deleteByIdAndUserId(existing.id(), userId);
    }

    @Transactional(readOnly = true)
    public List<PortfolioTransactionResponse> listTransactions(UUID userId, UUID portfolioId) {
        Portfolio portfolio = requireOwnedPortfolio(portfolioId, userId);
        List<PortfolioTransaction> transactions =
                portfolioRepository.findTransactionsByPortfolioId(portfolio.id());
        Map<UUID, Instrument> instrumentsById = loadInstrumentMap(transactions);

        return transactions.stream()
                .map(tx -> toTransactionResponse(tx, instrumentsById.get(tx.instrumentId())))
                .toList();
    }

    @Transactional
    public PortfolioTransactionResponse addTransaction(
            UUID userId, UUID portfolioId, CreateTransactionRequest request) {
        Portfolio portfolio = requireOwnedPortfolio(portfolioId, userId);
        String symbol = normalizeSymbol(request.symbol());
        Instrument instrument =
                instrumentRepository
                        .findBySymbol(symbol)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.NOT_FOUND,
                                                "Instrument not found: " + symbol));

        if (!portfolio.baseCurrency().equalsIgnoreCase(instrument.currency())) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Instrument currency ("
                            + instrument.currency()
                            + ") does not match portfolio base currency ("
                            + portfolio.baseCurrency()
                            + ")");
        }

        BigDecimal feeAmount =
                FinancialMath.scaleMoney(
                        request.feeAmount() != null ? request.feeAmount() : BigDecimal.ZERO);
        BigDecimal taxAmount =
                FinancialMath.scaleMoney(
                        request.taxAmount() != null ? request.taxAmount() : BigDecimal.ZERO);

        if (portfolio.feeTaxPolicy() == FeeTaxPolicy.EXCLUDED_MVP
                && (feeAmount.compareTo(BigDecimal.ZERO) > 0
                        || taxAmount.compareTo(BigDecimal.ZERO) > 0)) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Fees and taxes must be 0.00 when portfolio feeTaxPolicy is EXCLUDED_MVP");
        }

        Instant now = clock.instant();
        Instant executedAt = request.executedAt() != null ? request.executedAt() : now;
        if (executedAt.isAfter(now.plusSeconds(60))) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR, "executedAt must not be in the future");
        }

        PortfolioTransaction candidate;
        try {
            candidate =
                    new PortfolioTransaction(
                            UUID.randomUUID(),
                            portfolio.id(),
                            instrument.id(),
                            request.side(),
                            request.quantity(),
                            request.unitPrice(),
                            feeAmount,
                            taxAmount,
                            instrument.currency(),
                            executedAt,
                            normalizeOptionalText(request.notes()),
                            now);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, ex.getMessage(), ex);
        }

        List<PortfolioTransaction> existing =
                portfolioRepository.findTransactionsByPortfolioId(portfolio.id());
        List<PortfolioTransaction> combined = new ArrayList<>(existing.size() + 1);
        combined.addAll(existing);
        combined.add(candidate);

        try {
            PortfolioCalculator.calculate(portfolio.feeTaxPolicy(), combined);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, ex.getMessage(), ex);
        }

        PortfolioTransaction saved = portfolioRepository.saveTransaction(candidate);
        return toTransactionResponse(saved, instrument);
    }

    @Transactional
    public void deleteTransaction(UUID userId, UUID portfolioId, UUID transactionId) {
        Portfolio portfolio = requireOwnedPortfolio(portfolioId, userId);
        PortfolioTransaction target =
                portfolioRepository
                        .findTransactionByIdAndPortfolioId(transactionId, portfolio.id())
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.NOT_FOUND,
                                                "Portfolio transaction not found"));

        List<PortfolioTransaction> remaining =
                portfolioRepository.findTransactionsByPortfolioId(portfolio.id()).stream()
                        .filter(tx -> !tx.id().equals(target.id()))
                        .toList();

        try {
            PortfolioCalculator.calculate(portfolio.feeTaxPolicy(), remaining);
        } catch (IllegalArgumentException ex) {
            throw new AppException(
                    ErrorCode.CONFLICT,
                    "Deleting this transaction would cause a negative open position on a subsequent SELL",
                    ex);
        }

        portfolioRepository.deleteTransactionByIdAndPortfolioId(target.id(), portfolio.id());
    }

    @Transactional(readOnly = true)
    public PortfolioPerformanceResponse getPerformance(UUID userId, UUID portfolioId) {
        Portfolio portfolio = requireOwnedPortfolio(portfolioId, userId);
        List<PortfolioTransaction> transactions =
                portfolioRepository.findTransactionsByPortfolioId(portfolio.id());
        LedgerCalculationResult ledger =
                PortfolioCalculator.calculate(portfolio.feeTaxPolicy(), transactions);
        Map<UUID, Instrument> instrumentsById = loadInstrumentMap(transactions);
        ProviderMetadata metadata = marketDataProvider.getProviderMetadata();

        List<DerivedPosition> openPositions =
                ledger.positions().values().stream()
                        .filter(pos -> pos.openQuantity().compareTo(BigDecimal.ZERO) > 0)
                        .toList();
        int closedPositionsCount = ledger.positions().size() - openPositions.size();

        List<PortfolioHoldingResponse> holdings = new ArrayList<>(openPositions.size());
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        boolean missingAnyQuote = false;
        Instant latestQuoteTimestamp = null;

        for (DerivedPosition pos : openPositions) {
            Instrument instrument = instrumentsById.get(pos.instrumentId());
            if (instrument == null) {
                missingAnyQuote = true;
                continue;
            }
            LatestQuote quote = safeLatestQuote(instrument.symbol());
            BigDecimal latestPrice = quote != null ? quote.price() : null;
            Instant quotedAt = quote != null ? quote.quotedAt() : null;
            boolean simulated = quote != null ? quote.simulated() : metadata.simulated();

            BigDecimal marketValue = null;
            BigDecimal unrealizedPnl = null;
            BigDecimal unrealizedPnlPercent = null;

            if (latestPrice != null) {
                marketValue =
                        FinancialMath.scaleMoney(
                                pos.openQuantity().multiply(latestPrice, FinancialMath.MATH_CONTEXT));
                unrealizedPnl =
                        FinancialMath.scaleMoney(
                                marketValue.subtract(pos.costBasis(), FinancialMath.MATH_CONTEXT));
                unrealizedPnlPercent =
                        pos.costBasis().compareTo(BigDecimal.ZERO) > 0
                                ? FinancialMath.divide(
                                        unrealizedPnl.multiply(
                                                ONE_HUNDRED, FinancialMath.MATH_CONTEXT),
                                        pos.costBasis(),
                                        FinancialMath.PERCENTAGE_SCALE)
                                : FinancialMath.scalePercentage(BigDecimal.ZERO);

                totalMarketValue =
                        totalMarketValue.add(marketValue, FinancialMath.MATH_CONTEXT);
                if (latestQuoteTimestamp == null
                        || (quotedAt != null && quotedAt.isAfter(latestQuoteTimestamp))) {
                    latestQuoteTimestamp = quotedAt;
                }
            } else {
                missingAnyQuote = true;
            }

            holdings.add(
                    new PortfolioHoldingResponse(
                            instrument.id(),
                            instrument.symbol(),
                            instrument.name(),
                            instrument.exchangeCode(),
                            instrument.currency(),
                            pos.openQuantity(),
                            pos.averageCost(),
                            pos.costBasis(),
                            latestPrice,
                            quotedAt,
                            simulated,
                            marketValue,
                            unrealizedPnl,
                            unrealizedPnlPercent,
                            pos.realizedPnl(),
                            pos.totalFees(),
                            pos.totalTaxes()));
        }

        holdings.sort(Comparator.comparing(PortfolioHoldingResponse::symbol));

        FreshnessMetadataResponse freshness =
                freshnessEvaluator.evaluate(metadata, latestQuoteTimestamp, Optional.empty());

        BigDecimal scaledMarketValue;
        BigDecimal scaledUnrealized;
        BigDecimal scaledUnrealizedPercent;
        BigDecimal scaledNetPnl;
        PortfolioValuationStatus valuationStatus;

        if (transactions.isEmpty()) {
            scaledMarketValue = FinancialMath.scaleMoney(BigDecimal.ZERO);
            scaledUnrealized = FinancialMath.scaleMoney(BigDecimal.ZERO);
            scaledUnrealizedPercent = FinancialMath.scalePercentage(BigDecimal.ZERO);
            scaledNetPnl = FinancialMath.scaleMoney(BigDecimal.ZERO);
            valuationStatus = PortfolioValuationStatus.EMPTY;
        } else if (openPositions.isEmpty()) {
            scaledMarketValue = FinancialMath.scaleMoney(BigDecimal.ZERO);
            scaledUnrealized = FinancialMath.scaleMoney(BigDecimal.ZERO);
            scaledUnrealizedPercent = FinancialMath.scalePercentage(BigDecimal.ZERO);
            scaledNetPnl = ledger.totalRealizedPnl();
            valuationStatus = PortfolioValuationStatus.READY;
        } else if (missingAnyQuote) {
            scaledMarketValue = null;
            scaledUnrealized = null;
            scaledUnrealizedPercent = null;
            scaledNetPnl = null;
            valuationStatus = PortfolioValuationStatus.PARTIAL_UNAVAILABLE;
        } else {
            scaledMarketValue = FinancialMath.scaleMoney(totalMarketValue);
            scaledUnrealized =
                    FinancialMath.scaleMoney(
                            scaledMarketValue.subtract(
                                    ledger.totalCostBasis(), FinancialMath.MATH_CONTEXT));
            scaledUnrealizedPercent =
                    ledger.totalCostBasis().compareTo(BigDecimal.ZERO) > 0
                            ? FinancialMath.divide(
                                    scaledUnrealized.multiply(
                                            ONE_HUNDRED, FinancialMath.MATH_CONTEXT),
                                    ledger.totalCostBasis(),
                                    FinancialMath.PERCENTAGE_SCALE)
                            : FinancialMath.scalePercentage(BigDecimal.ZERO);
            scaledNetPnl =
                    FinancialMath.scaleMoney(
                            scaledUnrealized.add(
                                    ledger.totalRealizedPnl(), FinancialMath.MATH_CONTEXT));
            valuationStatus =
                    freshness.stale()
                            ? PortfolioValuationStatus.STALE_DATA
                            : PortfolioValuationStatus.READY;
        }

        return new PortfolioPerformanceResponse(
                portfolio.id(),
                portfolio.name(),
                portfolio.description(),
                portfolio.baseCurrency(),
                portfolio.feeTaxPolicy(),
                portfolio.feeTaxPolicy().description(),
                valuationStatus,
                ledger.totalCostBasis(),
                scaledMarketValue,
                scaledUnrealized,
                scaledUnrealizedPercent,
                ledger.totalRealizedPnl(),
                scaledNetPnl,
                ledger.totalFees(),
                ledger.totalTaxes(),
                holdings.size(),
                closedPositionsCount,
                transactions.size(),
                holdings,
                clock.instant(),
                DataSourceMetadataResponse.from(metadata),
                freshness,
                DISCLAIMER);
    }

    private Portfolio requireOwnedPortfolio(UUID portfolioId, UUID userId) {
        return portfolioRepository
                .findByIdAndUserId(portfolioId, userId)
                .orElseThrow(
                        () -> new AppException(ErrorCode.NOT_FOUND, "Portfolio not found"));
    }

    private Map<UUID, Instrument> loadInstrumentMap(List<PortfolioTransaction> transactions) {
        Set<UUID> ids =
                transactions.stream()
                        .map(PortfolioTransaction::instrumentId)
                        .collect(Collectors.toSet());
        return instrumentRepository.findAllByIds(ids).stream()
                .collect(Collectors.toMap(Instrument::id, Function.identity()));
    }

    private LatestQuote safeLatestQuote(String symbol) {
        try {
            return marketDataProvider.getLatestQuote(symbol);
        } catch (MarketDataProviderException | IllegalStateException ex) {
            log.warn("Quote lookup failed for portfolio symbol {}: {}", symbol, ex.getMessage());
            return null;
        }
    }

    private static PortfolioTransactionResponse toTransactionResponse(
            PortfolioTransaction tx, Instrument instrument) {
        BigDecimal grossAmount =
                FinancialMath.scaleMoney(
                        tx.quantity().multiply(tx.unitPrice(), FinancialMath.MATH_CONTEXT));
        return new PortfolioTransactionResponse(
                tx.id(),
                tx.instrumentId(),
                instrument != null ? instrument.symbol() : "UNKNOWN",
                instrument != null ? instrument.name() : "Unknown Instrument",
                tx.side(),
                tx.quantity(),
                tx.unitPrice(),
                grossAmount,
                tx.feeAmount(),
                tx.taxAmount(),
                tx.currency(),
                tx.executedAt(),
                tx.notes(),
                tx.createdAt());
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

    private static String normalizeCurrency(String rawCurrency) {
        if (rawCurrency == null || rawCurrency.isBlank()) {
            return "TRY";
        }
        return rawCurrency.trim().toUpperCase(Locale.ROOT);
    }
}
