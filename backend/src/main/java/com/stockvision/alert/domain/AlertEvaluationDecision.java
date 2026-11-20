package com.stockvision.alert.domain;

/**
 * Pure domain decision produced when evaluating an alert rule against an observed value.
 */
public record AlertEvaluationDecision(
        EvaluationOutcomeType outcome,
        AlertTriggerState nextTriggerState,
        String idempotencyKey,
        String reason) {
}
