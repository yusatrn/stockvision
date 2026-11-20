package com.stockvision.common.api;

import com.stockvision.marketdata.domain.MarketDataProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides the public v1 health check endpoint for operational verification.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Health", description = "System health and operational status")
public class HealthController {

    private static final String DISCLAIMER =
            "StockVision AI bir piyasa analizi ve yatırım karar destek aracıdır; yatırım danışmanlığı veya otomatik emir iletim sistemi değildir.";

    private final Clock clock;
    private final String applicationName;
    private final ObjectProvider<MarketDataProvider> marketDataProvider;

    public HealthController(
            Clock clock,
            @Value("${spring.application.name:stockvision-backend}") String applicationName,
            ObjectProvider<MarketDataProvider> marketDataProvider) {
        this.clock = clock;
        this.applicationName = applicationName;
        this.marketDataProvider = marketDataProvider;
    }

    @GetMapping("/health")
    @Operation(
            summary = "Check API health",
            description =
                    "Returns backend health status, UTC timestamp, active data provider mode, and decision-support disclaimer.")
    public HealthResponse health() {
        MarketDataProvider provider = marketDataProvider.getIfAvailable();
        String providerMode =
                provider != null ? provider.getProviderMetadata().mode().name() : "UNCONFIGURED";
        return new HealthResponse(
                "UP",
                applicationName,
                Instant.now(clock),
                providerMode,
                DISCLAIMER);
    }
}
