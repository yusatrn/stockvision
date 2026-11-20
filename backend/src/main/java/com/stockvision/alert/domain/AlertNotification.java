package com.stockvision.alert.domain;

import com.stockvision.marketdata.domain.DataProviderMode;
import com.stockvision.marketdata.domain.FreshnessStatus;
import com.stockvision.technicalanalysis.domain.IndicatorType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable persisted alert notification carrying provider source and freshness disclosure.
 */
public record AlertNotification(
        UUID id,
        UUID alertRuleId,
        UUID userId,
        UUID instrumentId,
        String symbol,
        AlertConditionType conditionType,
        IndicatorType indicatorType,
        String indicatorField,
        BigDecimal thresholdValue,
        BigDecimal observedValue,
        Instant dataTimestamp,
        String providerId,
        String providerDisplayName,
        DataProviderMode providerMode,
        boolean simulated,
        FreshnessStatus freshnessStatus,
        boolean staleData,
        String message,
        String idempotencyKey,
        Instant readAt,
        Instant createdAt) {

    public AlertNotification {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(alertRuleId, "alertRuleId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(instrumentId, "instrumentId must not be null");
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(conditionType, "conditionType must not be null");
        Objects.requireNonNull(thresholdValue, "thresholdValue must not be null");
        Objects.requireNonNull(observedValue, "observedValue must not be null");
        Objects.requireNonNull(dataTimestamp, "dataTimestamp must not be null");
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(providerDisplayName, "providerDisplayName must not be null");
        Objects.requireNonNull(providerMode, "providerMode must not be null");
        Objects.requireNonNull(freshnessStatus, "freshnessStatus must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    }
}
