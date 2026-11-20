package com.stockvision.alert.domain;

import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.technicalanalysis.domain.IndicatorType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * User-owned alert threshold rule domain model.
 */
public record AlertRule(
        UUID id,
        UUID userId,
        UUID instrumentId,
        AlertConditionType conditionType,
        IndicatorType indicatorType,
        String indicatorField,
        BarInterval barInterval,
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

    public AlertRule {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(instrumentId, "instrumentId must not be null");
        Objects.requireNonNull(conditionType, "conditionType must not be null");
        Objects.requireNonNull(barInterval, "barInterval must not be null");
        Objects.requireNonNull(thresholdValue, "thresholdValue must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(triggerState, "triggerState must not be null");
        if (cooldownMinutes < 1 || cooldownMinutes > 43_200) {
            throw new IllegalArgumentException("cooldownMinutes must be between 1 and 43200");
        }
    }
}
