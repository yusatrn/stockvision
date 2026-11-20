package com.stockvision.alert.api;

import com.stockvision.alert.domain.AlertConditionType;
import com.stockvision.alert.domain.AlertStatus;
import com.stockvision.alert.domain.AlertTriggerState;
import com.stockvision.technicalanalysis.domain.IndicatorType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Alert rule response including cooldown/trigger state telemetry.
 */
public record AlertRuleResponse(
        UUID id,
        UUID instrumentId,
        String symbol,
        String instrumentName,
        AlertConditionType conditionType,
        IndicatorType indicatorType,
        String indicatorField,
        String interval,
        BigDecimal thresholdValue,
        int cooldownMinutes,
        AlertStatus status,
        AlertTriggerState triggerState,
        Instant lastEvaluatedAt,
        Instant lastTriggeredAt,
        BigDecimal lastObservedValue,
        String note,
        Instant createdAt,
        Instant updatedAt) {
}
