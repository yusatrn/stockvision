package com.stockvision.alert.api;

import com.stockvision.alert.domain.AlertConditionType;
import com.stockvision.alert.domain.AlertNotification;
import com.stockvision.marketdata.domain.DataProviderMode;
import com.stockvision.marketdata.domain.FreshnessStatus;
import com.stockvision.technicalanalysis.domain.IndicatorType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Alert notification entry disclosing the underlying data source, mode, and freshness at trigger time.
 */
public record AlertNotificationResponse(
        UUID id,
        UUID alertRuleId,
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
        boolean read,
        Instant readAt,
        Instant createdAt) {

    public static AlertNotificationResponse from(AlertNotification n) {
        return new AlertNotificationResponse(
                n.id(),
                n.alertRuleId(),
                n.symbol(),
                n.conditionType(),
                n.indicatorType(),
                n.indicatorField(),
                n.thresholdValue(),
                n.observedValue(),
                n.dataTimestamp(),
                n.providerId(),
                n.providerDisplayName(),
                n.providerMode(),
                n.simulated(),
                n.freshnessStatus(),
                n.staleData(),
                n.message(),
                n.readAt() != null,
                n.readAt(),
                n.createdAt());
    }
}
