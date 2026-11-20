package com.stockvision.portfolio.api;

import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import com.stockvision.marketdata.api.FreshnessMetadataResponse;
import com.stockvision.portfolio.domain.FeeTaxPolicy;
import com.stockvision.portfolio.domain.PortfolioValuationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full derived virtual portfolio valuation, separating realized P&L from open unrealized P&L.
 */
public record PortfolioPerformanceResponse(
        UUID portfolioId,
        String name,
        String description,
        String baseCurrency,
        FeeTaxPolicy feeTaxPolicy,
        String feeTaxPolicyNote,
        PortfolioValuationStatus valuationStatus,
        BigDecimal totalCostBasis,
        BigDecimal totalMarketValue,
        BigDecimal totalUnrealizedPnl,
        BigDecimal totalUnrealizedPnlPercent,
        BigDecimal totalRealizedPnl,
        BigDecimal totalNetPnl,
        BigDecimal totalFees,
        BigDecimal totalTaxes,
        int openPositionsCount,
        int closedPositionsCount,
        int transactionCount,
        List<PortfolioHoldingResponse> holdings,
        Instant calculatedAt,
        DataSourceMetadataResponse dataSource,
        FreshnessMetadataResponse freshness,
        String disclaimer) {
}
