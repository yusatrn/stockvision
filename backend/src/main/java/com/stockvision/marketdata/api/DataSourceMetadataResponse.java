package com.stockvision.marketdata.api;

import com.stockvision.marketdata.domain.DataProviderMode;
import com.stockvision.marketdata.domain.ProviderMetadata;

/**
 * Public metadata describing the active market data source and licensing posture.
 */
public record DataSourceMetadataResponse(
        String providerId,
        String displayName,
        DataProviderMode mode,
        boolean simulated,
        int delayMinutes,
        String sourceTimezone,
        String licenseNote) {

    public static DataSourceMetadataResponse from(ProviderMetadata metadata) {
        return new DataSourceMetadataResponse(
                metadata.providerId(),
                metadata.displayName(),
                metadata.mode(),
                metadata.simulated(),
                metadata.delayMinutes(),
                metadata.sourceTimezone(),
                metadata.licenseNote());
    }
}
