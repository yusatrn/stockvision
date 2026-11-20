package com.stockvision.alert.infrastructure;

import com.stockvision.alert.domain.AlertConditionType;
import com.stockvision.alert.domain.AlertNotification;
import com.stockvision.marketdata.domain.DataProviderMode;
import com.stockvision.marketdata.domain.FreshnessStatus;
import com.stockvision.technicalanalysis.domain.IndicatorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code alert_notifications} table.
 */
@Entity
@Table(name = "alert_notifications")
public class AlertNotificationJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "alert_rule_id", nullable = false, updatable = false)
    private UUID alertRuleId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "instrument_id", nullable = false, updatable = false)
    private UUID instrumentId;

    @Column(name = "symbol", nullable = false, updatable = false, length = 24)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, updatable = false, length = 32)
    private AlertConditionType conditionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "indicator_type", updatable = false, length = 32)
    private IndicatorType indicatorType;

    @Column(name = "indicator_field", updatable = false, length = 32)
    private String indicatorField;

    @Column(name = "threshold_value", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal thresholdValue;

    @Column(name = "observed_value", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal observedValue;

    @Column(name = "data_timestamp", nullable = false, updatable = false)
    private Instant dataTimestamp;

    @Column(name = "provider_id", nullable = false, updatable = false, length = 64)
    private String providerId;

    @Column(name = "provider_display_name", nullable = false, updatable = false, length = 120)
    private String providerDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_mode", nullable = false, updatable = false, length = 24)
    private DataProviderMode providerMode;

    @Column(name = "simulated", nullable = false, updatable = false)
    private boolean simulated;

    @Enumerated(EnumType.STRING)
    @Column(name = "freshness_status", nullable = false, updatable = false, length = 24)
    private FreshnessStatus freshnessStatus;

    @Column(name = "stale_data", nullable = false, updatable = false)
    private boolean staleData;

    @Column(name = "message", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 160)
    private String idempotencyKey;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AlertNotificationJpaEntity() {
    }

    public static AlertNotificationJpaEntity fromDomain(AlertNotification notification) {
        AlertNotificationJpaEntity entity = new AlertNotificationJpaEntity();
        entity.id = notification.id();
        entity.alertRuleId = notification.alertRuleId();
        entity.userId = notification.userId();
        entity.instrumentId = notification.instrumentId();
        entity.symbol = notification.symbol();
        entity.conditionType = notification.conditionType();
        entity.indicatorType = notification.indicatorType();
        entity.indicatorField = notification.indicatorField();
        entity.thresholdValue = notification.thresholdValue();
        entity.observedValue = notification.observedValue();
        entity.dataTimestamp = notification.dataTimestamp();
        entity.providerId = notification.providerId();
        entity.providerDisplayName = notification.providerDisplayName();
        entity.providerMode = notification.providerMode();
        entity.simulated = notification.simulated();
        entity.freshnessStatus = notification.freshnessStatus();
        entity.staleData = notification.staleData();
        entity.message = notification.message();
        entity.idempotencyKey = notification.idempotencyKey();
        entity.readAt = notification.readAt();
        entity.createdAt = notification.createdAt() != null ? notification.createdAt() : Instant.now();
        return entity;
    }

    public void markRead(Instant readAt) {
        if (this.readAt == null) {
            this.readAt = readAt;
        }
    }

    public AlertNotification toDomain() {
        return new AlertNotification(
                id,
                alertRuleId,
                userId,
                instrumentId,
                symbol,
                conditionType,
                indicatorType,
                indicatorField,
                thresholdValue,
                observedValue,
                dataTimestamp,
                providerId,
                providerDisplayName,
                providerMode,
                simulated,
                freshnessStatus,
                staleData,
                message,
                idempotencyKey,
                readAt,
                createdAt);
    }
}
