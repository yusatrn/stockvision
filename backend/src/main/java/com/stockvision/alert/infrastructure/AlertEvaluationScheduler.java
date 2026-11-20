package com.stockvision.alert.infrastructure;

import com.stockvision.alert.application.AlertEvaluationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic job evaluating active user alert rules using fresh or simulated market data.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.alerts.scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AlertEvaluationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertEvaluationScheduler.class);

    private final AlertEvaluationService alertEvaluationService;

    public AlertEvaluationScheduler(AlertEvaluationService alertEvaluationService) {
        this.alertEvaluationService = alertEvaluationService;
    }

    @Scheduled(fixedDelayString = "${app.alerts.scheduler.fixed-delay-ms:60000}")
    public void runScheduledEvaluation() {
        try {
            alertEvaluationService.evaluateAllActiveRules();
        } catch (RuntimeException ex) {
            log.warn("Scheduled alert evaluation run failed: {}", ex.getMessage());
        }
    }
}
