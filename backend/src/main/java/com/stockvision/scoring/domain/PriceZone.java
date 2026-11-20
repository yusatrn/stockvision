package com.stockvision.scoring.domain;

import com.stockvision.common.domain.FinancialMath;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Closed price band derived mathematically from technical indicators and trailing volatility.
 */
public record PriceZone(BigDecimal low, BigDecimal high, String label, String rationale) {

    public PriceZone {
        Objects.requireNonNull(low, "low must not be null");
        Objects.requireNonNull(high, "high must not be null");
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(rationale, "rationale must not be null");
        low = FinancialMath.scalePrice(low);
        high = FinancialMath.scalePrice(high);
        if (low.compareTo(high) > 0) {
            throw new IllegalArgumentException("low must not exceed high");
        }
    }
}
