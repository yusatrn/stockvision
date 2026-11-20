package com.stockvision.alert.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables scheduled background evaluation of active alert rules unless disabled via config.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "app.alerts.scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AlertSchedulingConfig {
}
