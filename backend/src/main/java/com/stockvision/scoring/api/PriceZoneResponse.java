package com.stockvision.scoring.api;

import com.stockvision.scoring.domain.PriceZone;
import java.math.BigDecimal;

/**
 * Closed technical support/watch or resistance/realization reference zone.
 */
public record PriceZoneResponse(BigDecimal low, BigDecimal high, String label, String rationale) {

    public static PriceZoneResponse from(PriceZone zone) {
        if (zone == null) {
            return null;
        }
        return new PriceZoneResponse(zone.low(), zone.high(), zone.label(), zone.rationale());
    }
}
