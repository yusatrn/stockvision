package com.stockvision.portfolio.domain;

import com.stockvision.common.domain.FinancialMath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic, framework-free moving weighted-average cost calculator derived solely from
 * virtual BUY/SELL ledger transactions using {@link BigDecimal} and {@link FinancialMath}.
 */
public final class PortfolioCalculator {

    private static final Comparator<PortfolioTransaction> CHRONOLOGICAL_ORDER =
            Comparator.comparing(PortfolioTransaction::executedAt)
                    .thenComparing(
                            tx -> tx.createdAt() != null ? tx.createdAt() : Instant.EPOCH)
                    .thenComparing(PortfolioTransaction::id);

    private PortfolioCalculator() {
    }

    public static LedgerCalculationResult calculate(
            FeeTaxPolicy feeTaxPolicy, List<PortfolioTransaction> transactions) {
        Objects.requireNonNull(feeTaxPolicy, "feeTaxPolicy must not be null");
        if (transactions == null || transactions.isEmpty()) {
            return new LedgerCalculationResult(
                    Map.of(),
                    FinancialMath.scaleMoney(BigDecimal.ZERO),
                    FinancialMath.scaleMoney(BigDecimal.ZERO),
                    FinancialMath.scaleMoney(BigDecimal.ZERO),
                    FinancialMath.scaleMoney(BigDecimal.ZERO));
        }

        List<PortfolioTransaction> ordered = new ArrayList<>(transactions);
        ordered.sort(CHRONOLOGICAL_ORDER);

        Map<UUID, Accumulator> byInstrument = new LinkedHashMap<>();

        for (PortfolioTransaction tx : ordered) {
            Accumulator acc = byInstrument.computeIfAbsent(tx.instrumentId(), id -> new Accumulator());
            BigDecimal txFee =
                    feeTaxPolicy == FeeTaxPolicy.EXPLICIT_PER_TXN ? tx.feeAmount() : BigDecimal.ZERO;
            BigDecimal txTax =
                    feeTaxPolicy == FeeTaxPolicy.EXPLICIT_PER_TXN ? tx.taxAmount() : BigDecimal.ZERO;
            BigDecimal txCharges = txFee.add(txTax, FinancialMath.MATH_CONTEXT);

            acc.totalFees = acc.totalFees.add(txFee, FinancialMath.MATH_CONTEXT);
            acc.totalTaxes = acc.totalTaxes.add(txTax, FinancialMath.MATH_CONTEXT);

            BigDecimal grossAmount = tx.quantity().multiply(tx.unitPrice(), FinancialMath.MATH_CONTEXT);

            if (tx.side() == TransactionSide.BUY) {
                BigDecimal addedCost = grossAmount.add(txCharges, FinancialMath.MATH_CONTEXT);
                acc.openQuantity = acc.openQuantity.add(tx.quantity(), FinancialMath.MATH_CONTEXT);
                acc.openCostBasis = acc.openCostBasis.add(addedCost, FinancialMath.MATH_CONTEXT);
                acc.buyCount++;
            } else {
                if (tx.quantity().compareTo(acc.openQuantity) > 0) {
                    throw new IllegalArgumentException(
                            "Insufficient open quantity to sell "
                                    + tx.quantity().toPlainString()
                                    + " units at "
                                    + tx.executedAt()
                                    + " (available: "
                                    + FinancialMath.scaleQuantity(acc.openQuantity).toPlainString()
                                    + ")");
                }

                BigDecimal avgCost =
                        acc.openCostBasis.divide(acc.openQuantity, FinancialMath.MATH_CONTEXT);
                BigDecimal costOfSold = avgCost.multiply(tx.quantity(), FinancialMath.MATH_CONTEXT);
                BigDecimal netProceeds = grossAmount.subtract(txCharges, FinancialMath.MATH_CONTEXT);
                BigDecimal tradeRealized = netProceeds.subtract(costOfSold, FinancialMath.MATH_CONTEXT);

                acc.realizedPnl = acc.realizedPnl.add(tradeRealized, FinancialMath.MATH_CONTEXT);
                acc.openQuantity =
                        acc.openQuantity.subtract(tx.quantity(), FinancialMath.MATH_CONTEXT);

                if (acc.openQuantity.compareTo(BigDecimal.ZERO) == 0) {
                    acc.openQuantity = BigDecimal.ZERO;
                    acc.openCostBasis = BigDecimal.ZERO;
                } else {
                    acc.openCostBasis =
                            acc.openCostBasis.subtract(costOfSold, FinancialMath.MATH_CONTEXT);
                    if (acc.openCostBasis.compareTo(BigDecimal.ZERO) < 0) {
                        acc.openCostBasis = BigDecimal.ZERO;
                    }
                }
                acc.sellCount++;
            }
        }

        Map<UUID, DerivedPosition> positions = new LinkedHashMap<>();
        BigDecimal totalCostBasis = BigDecimal.ZERO;
        BigDecimal totalRealizedPnl = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;
        BigDecimal totalTaxes = BigDecimal.ZERO;

        for (Map.Entry<UUID, Accumulator> entry : byInstrument.entrySet()) {
            UUID instrumentId = entry.getKey();
            Accumulator acc = entry.getValue();

            BigDecimal scaledQty = FinancialMath.scaleQuantity(acc.openQuantity);
            BigDecimal scaledAvgCost =
                    scaledQty.compareTo(BigDecimal.ZERO) > 0
                            ? acc.openCostBasis.divide(
                                    scaledQty,
                                    FinancialMath.PRICE_SCALE,
                                    FinancialMath.ROUNDING_MODE)
                            : FinancialMath.scalePrice(BigDecimal.ZERO);
            BigDecimal scaledCostBasis =
                    scaledQty.compareTo(BigDecimal.ZERO) > 0
                            ? FinancialMath.scaleMoney(acc.openCostBasis)
                            : FinancialMath.scaleMoney(BigDecimal.ZERO);
            BigDecimal scaledRealized = FinancialMath.scaleMoney(acc.realizedPnl);
            BigDecimal scaledFees = FinancialMath.scaleMoney(acc.totalFees);
            BigDecimal scaledTaxes = FinancialMath.scaleMoney(acc.totalTaxes);

            positions.put(
                    instrumentId,
                    new DerivedPosition(
                            instrumentId,
                            scaledQty,
                            scaledAvgCost,
                            scaledCostBasis,
                            scaledRealized,
                            scaledFees,
                            scaledTaxes,
                            acc.buyCount,
                            acc.sellCount));

            totalCostBasis = totalCostBasis.add(scaledCostBasis, FinancialMath.MATH_CONTEXT);
            totalRealizedPnl = totalRealizedPnl.add(scaledRealized, FinancialMath.MATH_CONTEXT);
            totalFees = totalFees.add(scaledFees, FinancialMath.MATH_CONTEXT);
            totalTaxes = totalTaxes.add(scaledTaxes, FinancialMath.MATH_CONTEXT);
        }

        return new LedgerCalculationResult(
                Map.copyOf(positions),
                FinancialMath.scaleMoney(totalCostBasis),
                FinancialMath.scaleMoney(totalRealizedPnl),
                FinancialMath.scaleMoney(totalFees),
                FinancialMath.scaleMoney(totalTaxes));
    }

    private static final class Accumulator {
        private BigDecimal openQuantity = BigDecimal.ZERO;
        private BigDecimal openCostBasis = BigDecimal.ZERO;
        private BigDecimal realizedPnl = BigDecimal.ZERO;
        private BigDecimal totalFees = BigDecimal.ZERO;
        private BigDecimal totalTaxes = BigDecimal.ZERO;
        private int buyCount;
        private int sellCount;
    }
}
