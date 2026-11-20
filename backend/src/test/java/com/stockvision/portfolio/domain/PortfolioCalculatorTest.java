package com.stockvision.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortfolioCalculatorTest {

    private static final UUID PORTFOLIO_ID = UUID.randomUUID();
    private static final UUID THYAO_ID = UUID.randomUUID();
    private static final UUID GARAN_ID = UUID.randomUUID();

    @Test
    void derivesWeightedAverageCostRealizedAndRemainingCostBasisAcrossPartialAndFullSells() {
        PortfolioTransaction buy1 =
                tx(
                        THYAO_ID,
                        TransactionSide.BUY,
                        "10",
                        "100.00",
                        "0",
                        "0",
                        Instant.parse("2026-11-01T10:00:00Z"));
        PortfolioTransaction buy2 =
                tx(
                        THYAO_ID,
                        TransactionSide.BUY,
                        "20",
                        "130.00",
                        "0",
                        "0",
                        Instant.parse("2026-11-02T10:00:00Z"));
        PortfolioTransaction partialSell =
                tx(
                        THYAO_ID,
                        TransactionSide.SELL,
                        "15",
                        "140.00",
                        "0",
                        "0",
                        Instant.parse("2026-11-03T10:00:00Z"));

        // Deliberately pass out of chronological order to verify deterministic ledger replay sorting.
        LedgerCalculationResult result =
                PortfolioCalculator.calculate(
                        FeeTaxPolicy.EXCLUDED_MVP, List.of(partialSell, buy2, buy1));

        DerivedPosition pos = result.positions().get(THYAO_ID);
        assertThat(pos).isNotNull();
        assertThat(pos.openQuantity()).isEqualByComparingTo("15.000000");
        assertThat(pos.averageCost()).isEqualByComparingTo("120.0000");
        assertThat(pos.costBasis()).isEqualByComparingTo("1800.00");
        assertThat(pos.realizedPnl()).isEqualByComparingTo("300.00");

        PortfolioTransaction finalSell =
                tx(
                        THYAO_ID,
                        TransactionSide.SELL,
                        "15",
                        "110.00",
                        "0",
                        "0",
                        Instant.parse("2026-11-04T10:00:00Z"));
        LedgerCalculationResult closed =
                PortfolioCalculator.calculate(
                        FeeTaxPolicy.EXCLUDED_MVP, List.of(buy1, buy2, partialSell, finalSell));

        DerivedPosition closedPos = closed.positions().get(THYAO_ID);
        assertThat(closedPos.openQuantity()).isEqualByComparingTo("0.000000");
        assertThat(closedPos.averageCost()).isEqualByComparingTo("0.0000");
        assertThat(closedPos.costBasis()).isEqualByComparingTo("0.00");
        // +300.00 on partialSell + (110 - 120) * 15 = +150.00 net realized
        assertThat(closedPos.realizedPnl()).isEqualByComparingTo("150.00");
        assertThat(closed.totalCostBasis()).isEqualByComparingTo("0.00");
        assertThat(closed.totalRealizedPnl()).isEqualByComparingTo("150.00");
    }

    @Test
    void incorporatesFeesAndTaxesUnderExplicitPolicyAndIgnoresUnderExcludedMvp() {
        PortfolioTransaction buy =
                tx(
                        GARAN_ID,
                        TransactionSide.BUY,
                        "10",
                        "100.00",
                        "15.00",
                        "5.00",
                        Instant.parse("2026-11-01T10:00:00Z"));
        PortfolioTransaction sell =
                tx(
                        GARAN_ID,
                        TransactionSide.SELL,
                        "5",
                        "120.00",
                        "10.00",
                        "2.00",
                        Instant.parse("2026-11-05T10:00:00Z"));

        // Explicit policy: buy total cost = 1000 + 20 = 1020 -> avg cost 102.0000
        // Sell 5 @ 120 = 600 gross - 12 charges = 588 net proceeds; costOfSold = 510 -> realized = +78.00
        LedgerCalculationResult explicit =
                PortfolioCalculator.calculate(FeeTaxPolicy.EXPLICIT_PER_TXN, List.of(buy, sell));
        DerivedPosition expPos = explicit.positions().get(GARAN_ID);
        assertThat(expPos.openQuantity()).isEqualByComparingTo("5.000000");
        assertThat(expPos.averageCost()).isEqualByComparingTo("102.0000");
        assertThat(expPos.costBasis()).isEqualByComparingTo("510.00");
        assertThat(expPos.realizedPnl()).isEqualByComparingTo("78.00");
        assertThat(explicit.totalFees()).isEqualByComparingTo("25.00");
        assertThat(explicit.totalTaxes()).isEqualByComparingTo("7.00");

        // Excluded MVP: ignores fee/tax
        LedgerCalculationResult excluded =
                PortfolioCalculator.calculate(FeeTaxPolicy.EXCLUDED_MVP, List.of(buy, sell));
        DerivedPosition excPos = excluded.positions().get(GARAN_ID);
        assertThat(excPos.averageCost()).isEqualByComparingTo("100.0000");
        assertThat(excPos.realizedPnl()).isEqualByComparingTo("100.00");
        assertThat(excluded.totalFees()).isEqualByComparingTo("0.00");
        assertThat(excluded.totalTaxes()).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsOversellingOrSellBeforeBuy() {
        PortfolioTransaction buy =
                tx(
                        THYAO_ID,
                        TransactionSide.BUY,
                        "10",
                        "200.00",
                        "0",
                        "0",
                        Instant.parse("2026-11-05T10:00:00Z"));
        PortfolioTransaction earlierSell =
                tx(
                        THYAO_ID,
                        TransactionSide.SELL,
                        "2",
                        "210.00",
                        "0",
                        "0",
                        Instant.parse("2026-11-04T10:00:00Z"));

        assertThatThrownBy(
                        () ->
                                PortfolioCalculator.calculate(
                                        FeeTaxPolicy.EXCLUDED_MVP, List.of(buy, earlierSell)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient open quantity");
    }

    private static PortfolioTransaction tx(
            UUID instrumentId,
            TransactionSide side,
            String qty,
            String price,
            String fee,
            String tax,
            Instant executedAt) {
        return new PortfolioTransaction(
                UUID.randomUUID(),
                PORTFOLIO_ID,
                instrumentId,
                side,
                new BigDecimal(qty),
                new BigDecimal(price),
                new BigDecimal(fee),
                new BigDecimal(tax),
                "TRY",
                executedAt,
                null,
                executedAt);
    }
}
