package com.stockvision.marketdata.domain;

import java.util.Objects;

/**
 * Metadata describing the active {@link MarketDataProvider} source, licensing posture, and latency.
 */
public record ProviderMetadata(
        String providerId,
        String displayName,
        DataProviderMode mode,
        boolean simulated,
        int delayMinutes,
        String licenseNote,
        String sourceTimezone) {

    public ProviderMetadata {
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(licenseNote, "licenseNote must not be null");
        Objects.requireNonNull(sourceTimezone, "sourceTimezone must not be null");
        if (delayMinutes < 0) {
            throw new IllegalArgumentException("delayMinutes must not be negative");
        }
    }
}
