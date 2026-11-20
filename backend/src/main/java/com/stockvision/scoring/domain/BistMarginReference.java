package com.stockvision.scoring.domain;

import com.stockvision.common.domain.FinancialMath;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Mathematical daily ±10% BIST price margin reference derived solely from prior session close.
 * Explicitly informational and never constitutes a ceiling or floor prediction.
 */
public record BistMarginReference(
        BigDecimal referencePrice,
        BigDecimal previousClose,
        BigDecimal dailyChangePercent,
        BigDecimal theoreticalCeilingPrice,
        BigDecimal theoreticalFloorPrice,
        BigDecimal distanceToCeilingPercent,
        BigDecimal distanceToFloorPercent,
        String note) {

    public BistMarginReference {
        referencePrice =
                FinancialMath.scalePrice(
                        Objects.requireNonNull(referencePrice, "referencePrice must not be null"));
        previousClose =
                FinancialMath.scalePrice(
                        Objects.requireNonNull(previousClose, "previousClose must not be null"));
        dailyChangePercent =
                FinancialMath.scalePercentage(
                        Objects.requireNonNull(
                                dailyChangePercent, "dailyChangePercent must not be null"));
        theoreticalCeilingPrice =
                FinancialMath.scalePrice(
                        Objects.requireNonNull(
                                theoreticalCeilingPrice,
                                "theoreticalCeilingPrice must not be null"));
        theoreticalFloorPrice =
                FinancialMath.scalePrice(
                        Objects.requireNonNull(
                                theoreticalFloorPrice, "theoreticalFloorPrice must not be null"));
        distanceToCeilingPercent =
                FinancialMath.scalePercentage(
                        Objects.requireNonNull(
                                distanceToCeilingPercent,
                                "distanceToCeilingPercent must not be null"));
        distanceToFloorPercent =
                FinancialMath.scalePercentage(
                        Objects.requireNonNull(
                                distanceToFloorPercent, "distanceToFloorPercent must not be null"));
        Objects.requireNonNull(note, "note must not be null");
    }
}
