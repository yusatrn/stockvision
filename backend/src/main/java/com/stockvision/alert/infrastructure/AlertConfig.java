package com.stockvision.alert.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers alert configuration properties regardless of scheduler toggle state.
 */
@Configuration
@EnableConfigurationProperties(AlertProperties.class)
public class AlertConfig {
}
