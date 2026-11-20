package com.stockvision.scoring.api;

import com.stockvision.scoring.domain.BistMarginReference;
import java.math.BigDecimal;

/**
 * Mathematical daily ±10% BIST price margin reference derived from previous session close.
 */
public record BistMarginReferenceResponse(
        BigDecimal referencePrice,
        BigDecimal previousClose,
        BigDecimal dailyChangePercent,
        BigDecimal theoreticalCeilingPrice,
        BigDecimal theoreticalFloorPrice,
        BigDecimal distanceToCeilingPercent,
        BigDecimal distanceToFloorPercent,
        String note) {

    public static BistMarginReferenceResponse from(BistMarginReference margin) {
        if (margin == null) {
            return null;
        }
        return new BistMarginReferenceResponse(
                margin.referencePrice(),
                margin.previousClose(),
                margin.dailyChangePercent(),
                margin.theoreticalCeilingPrice(),
                margin.theoreticalFloorPrice(),
                margin.distanceToCeilingPercent(),
                margin.distanceToFloorPercent(),
                margin.note());
    }
}
