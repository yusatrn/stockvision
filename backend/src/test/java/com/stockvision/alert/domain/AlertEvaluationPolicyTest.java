package com.stockvision.alert.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.technicalanalysis.domain.IndicatorType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlertEvaluationPolicyTest {

    @Test
    void triggersWhenThresholdMetAndSuppressesRepeatedTriggersDuringCooldown() {
        Instant t0 = Instant.parse("2026-11-20T09:00:00Z");
        AlertRule armedRule = rule(AlertConditionType.PRICE_ABOVE, "300.000000", 60, AlertTriggerState.ARMED, null);

        AlertEvaluationDecision first =
                AlertEvaluationPolicy.evaluate(
                        armedRule,
                        new BigDecimal("305.00"),
                        t0,
                        false,
                        true,
                        t0);

        assertThat(first.outcome()).isEqualTo(EvaluationOutcomeType.TRIGGERED);
        assertThat(first.nextTriggerState()).isEqualTo(AlertTriggerState.TRIGGERED);
        assertThat(first.idempotencyKey()).isNotBlank();

        // Next evaluation 10 minutes later while price still >= 300 must be suppressed by cooldown
        AlertRule triggeredRule =
                rule(
                        AlertConditionType.PRICE_ABOVE,
                        "300.000000",
                        60,
                        AlertTriggerState.TRIGGERED,
                        t0);
        Instant t10m = t0.plusSeconds(600);
        AlertEvaluationDecision suppressed =
                AlertEvaluationPolicy.evaluate(
                        triggeredRule,
                        new BigDecimal("308.00"),
                        t10m,
                        false,
                        true,
                        t10m);

        assertThat(suppressed.outcome()).isEqualTo(EvaluationOutcomeType.COOLDOWN_SUPPRESSED);
        assertThat(suppressed.nextTriggerState()).isEqualTo(AlertTriggerState.TRIGGERED);
        assertThat(suppressed.idempotencyKey()).isNull();

        // After cooldown expires (61 minutes later), rule can emit another notification with new key
        Instant t61m = t0.plusSeconds(3660);
        AlertEvaluationDecision reTriggered =
                AlertEvaluationPolicy.evaluate(
                        triggeredRule,
                        new BigDecimal("312.00"),
                        t61m,
                        false,
                        true,
                        t61m);

        assertThat(reTriggered.outcome()).isEqualTo(EvaluationOutcomeType.TRIGGERED);
        assertThat(reTriggered.idempotencyKey()).isNotEqualTo(first.idempotencyKey());
    }

    @Test
    void reArmsRuleWhenConditionTurnsFalseAndReTriggersOnFreshCross() {
        Instant t0 = Instant.parse("2026-11-20T09:00:00Z");
        AlertRule triggeredRule =
                rule(
                        AlertConditionType.INDICATOR_BELOW,
                        "30.000000",
                        60,
                        AlertTriggerState.TRIGGERED,
                        t0);

        // RSI rises above 30 -> NOT_MET resets trigger state to ARMED
        AlertEvaluationDecision recovered =
                AlertEvaluationPolicy.evaluate(
                        triggeredRule,
                        new BigDecimal("42.50"),
                        t0.plusSeconds(60),
                        false,
                        true,
                        t0.plusSeconds(60));
        assertThat(recovered.outcome()).isEqualTo(EvaluationOutcomeType.NOT_MET);
        assertThat(recovered.nextTriggerState()).isEqualTo(AlertTriggerState.ARMED);

        // RSI falls back <= 30 -> triggers again because rule re-armed
        AlertRule reArmedRule =
                rule(
                        AlertConditionType.INDICATOR_BELOW,
                        "30.000000",
                        60,
                        AlertTriggerState.ARMED,
                        t0);
        AlertEvaluationDecision crossedAgain =
                AlertEvaluationPolicy.evaluate(
                        reArmedRule,
                        new BigDecimal("29.00"),
                        t0.plusSeconds(120),
                        false,
                        true,
                        t0.plusSeconds(120));
        assertThat(crossedAgain.outcome()).isEqualTo(EvaluationOutcomeType.TRIGGERED);
    }

    @Test
    void skipsWhenDataIsStaleOrMissing() {
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        AlertRule armedRule =
                rule(AlertConditionType.PRICE_ABOVE, "100.000000", 60, AlertTriggerState.ARMED, null);

        AlertEvaluationDecision stale =
                AlertEvaluationPolicy.evaluate(
                        armedRule,
                        new BigDecimal("150.00"),
                        now,
                        true,
                        true,
                        now);
        assertThat(stale.outcome()).isEqualTo(EvaluationOutcomeType.SKIPPED_STALE_DATA);
        assertThat(stale.nextTriggerState()).isEqualTo(AlertTriggerState.ARMED);

        AlertEvaluationDecision missing =
                AlertEvaluationPolicy.evaluate(armedRule, null, null, false, true, now);
        assertThat(missing.outcome()).isEqualTo(EvaluationOutcomeType.SKIPPED_INSUFFICIENT_DATA);
    }

    private static AlertRule rule(
            AlertConditionType conditionType,
            String threshold,
            int cooldownMinutes,
            AlertTriggerState triggerState,
            Instant lastTriggeredAt) {
        return new AlertRule(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                conditionType,
                conditionType.isIndicatorCondition() ? IndicatorType.RSI : null,
                conditionType.isIndicatorCondition() ? "value" : null,
                BarInterval.ONE_DAY,
                new BigDecimal(threshold),
                cooldownMinutes,
                AlertStatus.ACTIVE,
                triggerState,
                null,
                lastTriggeredAt,
                null,
                null,
                Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:00Z"));
    }
}
