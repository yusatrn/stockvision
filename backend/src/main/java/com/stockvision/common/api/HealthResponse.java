package com.stockvision.common.api;

import java.time.Instant;

/**
 * Operational health response payload exposed by {@link HealthController}.
 */
public record HealthResponse(
        String status,
        String service,
        Instant timestamp,
        String dataProviderMode,
        String disclaimer) {
}
