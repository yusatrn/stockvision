package com.stockvision.alert.infrastructure;

import com.stockvision.alert.domain.AlertConditionType;
import com.stockvision.alert.domain.AlertRule;
import com.stockvision.alert.domain.AlertStatus;
import com.stockvision.alert.domain.AlertTriggerState;
import com.stockvision.common.domain.AuditableEntity;
import com.stockvision.marketdata.domain.BarInterval;
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
 * JPA entity mapping the {@code alert_rules} table.
 */
@Entity
@Table(name = "alert_rules")
public class AlertRuleJpaEntity extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "instrument_id", nullable = false, updatable = false)
    private UUID instrumentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, length = 32)
    private AlertConditionType conditionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "indicator_type", length = 32)
    private IndicatorType indicatorType;

    @Column(name = "indicator_field", length = 32)
    private String indicatorField;

    @Column(name = "bar_interval", nullable = false, length = 16)
    private String barInterval;

    @Column(name = "threshold_value", nullable = false, precision = 19, scale = 6)
    private BigDecimal thresholdValue;

    @Column(name = "cooldown_minutes", nullable = false)
    private int cooldownMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AlertStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_state", nullable = false, length = 16)
    private AlertTriggerState triggerState;

    @Column(name = "last_evaluated_at")
    private Instant lastEvaluatedAt;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    @Column(name = "last_observed_value", precision = 19, scale = 6)
    private BigDecimal lastObservedValue;

    @Column(name = "note", length = 255)
    private String note;

    protected AlertRuleJpaEntity() {
    }

    public static AlertRuleJpaEntity fromDomain(AlertRule rule) {
        AlertRuleJpaEntity entity = new AlertRuleJpaEntity();
        entity.id = rule.id();
        entity.userId = rule.userId();
        entity.instrumentId = rule.instrumentId();
        entity.applyChanges(rule);
        return entity;
    }

    public void applyChanges(AlertRule rule) {
        this.conditionType = rule.conditionType();
        this.indicatorType = rule.indicatorType();
        this.indicatorField = rule.indicatorField();
        this.barInterval = rule.barInterval().code();
        this.thresholdValue = rule.thresholdValue();
        this.cooldownMinutes = rule.cooldownMinutes();
        this.status = rule.status();
        this.triggerState = rule.triggerState();
        this.lastEvaluatedAt = rule.lastEvaluatedAt();
        this.lastTriggeredAt = rule.lastTriggeredAt();
        this.lastObservedValue = rule.lastObservedValue();
        this.note = rule.note();
    }

    public AlertRule toDomain() {
        return new AlertRule(
                id,
                userId,
                instrumentId,
                conditionType,
                indicatorType,
                indicatorField,
                BarInterval.fromCode(barInterval),
                thresholdValue,
                cooldownMinutes,
                status,
                triggerState,
                lastEvaluatedAt,
                lastTriggeredAt,
                lastObservedValue,
                note,
                getCreatedAt(),
                getUpdatedAt());
    }
}
