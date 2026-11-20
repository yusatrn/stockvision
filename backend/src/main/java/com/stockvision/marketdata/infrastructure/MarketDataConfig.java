package com.stockvision.marketdata.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables market data module configuration properties.
 */
@Configuration
@EnableConfigurationProperties(MarketDataProperties.class)
public class MarketDataConfig {
}
