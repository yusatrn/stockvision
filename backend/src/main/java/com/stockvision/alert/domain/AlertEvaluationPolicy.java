package com.stockvision.alert.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Stateless domain policy enforcing freshness checks, re-arming hysteresis, cooldown windows,
 * and deterministic idempotency keys to avoid notification storms.
 */
public final class AlertEvaluationPolicy {

    private AlertEvaluationPolicy() {
    }

    public static AlertEvaluationDecision evaluate(
            AlertRule rule,
            BigDecimal observedValue,
            Instant dataTimestamp,
            boolean staleData,
            boolean rejectStaleData,
            Instant now) {
        Objects.requireNonNull(rule, "rule must not be null");
        Objects.requireNonNull(now, "now must not be null");

        if (staleData && rejectStaleData) {
            return new AlertEvaluationDecision(
                    EvaluationOutcomeType.SKIPPED_STALE_DATA,
                    rule.triggerState(),
                    null,
                    "Skipped alert evaluation because underlying market data is stale.");
        }

        if (observedValue == null || dataTimestamp == null) {
            return new AlertEvaluationDecision(
                    EvaluationOutcomeType.SKIPPED_INSUFFICIENT_DATA,
                    rule.triggerState(),
                    null,
                    "Insufficient market or indicator data to evaluate condition.");
        }

        boolean matched = matchesThreshold(rule.conditionType(), observedValue, rule.thresholdValue());
        if (!matched) {
            return new AlertEvaluationDecision(
                    EvaluationOutcomeType.NOT_MET,
                    AlertTriggerState.ARMED,
                    null,
                    "Observed value did not satisfy threshold condition; rule remains armed.");
        }

        if (rule.triggerState() == AlertTriggerState.TRIGGERED
                && rule.lastTriggeredAt() != null
                && now.isBefore(
                        rule.lastTriggeredAt().plus(Duration.ofMinutes(rule.cooldownMinutes())))) {
            return new AlertEvaluationDecision(
                    EvaluationOutcomeType.COOLDOWN_SUPPRESSED,
                    AlertTriggerState.TRIGGERED,
                    null,
                    "Threshold matched, but notification was suppressed by active cooldown window.");
        }

        long cooldownSeconds = Math.max(60L, rule.cooldownMinutes() * 60L);
        long cooldownBucket = Math.floorDiv(now.getEpochSecond(), cooldownSeconds);
        String idempotencyKey =
                rule.id() + ":" + dataTimestamp.toEpochMilli() + ":" + cooldownBucket;

        return new AlertEvaluationDecision(
                EvaluationOutcomeType.TRIGGERED,
                AlertTriggerState.TRIGGERED,
                idempotencyKey,
                "Threshold condition satisfied.");
    }

    public static boolean matchesThreshold(
            AlertConditionType conditionType, BigDecimal observedValue, BigDecimal thresholdValue) {
        int cmp = observedValue.compareTo(thresholdValue);
        return conditionType.isAbove() ? cmp >= 0 : cmp <= 0;
    }
}
