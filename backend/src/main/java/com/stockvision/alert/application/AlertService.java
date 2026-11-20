package com.stockvision.alert.application;

import com.stockvision.alert.api.AlertRuleListResponse;
import com.stockvision.alert.api.AlertRuleResponse;
import com.stockvision.alert.api.CreateAlertRequest;
import com.stockvision.alert.api.UpdateAlertRequest;
import com.stockvision.alert.domain.AlertConditionType;
import com.stockvision.alert.domain.AlertIndicatorFieldCatalog;
import com.stockvision.alert.domain.AlertRule;
import com.stockvision.alert.domain.AlertRuleRepository;
import com.stockvision.alert.domain.AlertStatus;
import com.stockvision.alert.domain.AlertTriggerState;
import com.stockvision.alert.infrastructure.AlertProperties;
import com.stockvision.common.domain.FinancialMath;
import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import com.stockvision.marketdata.api.FreshnessMetadataResponse;
import com.stockvision.marketdata.application.FreshnessEvaluator;
import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.marketdata.domain.MarketDataProvider;
import com.stockvision.marketdata.domain.ProviderMetadata;
import com.stockvision.stock.domain.Instrument;
import com.stockvision.stock.domain.InstrumentRepository;
import com.stockvision.stock.domain.SymbolNormalizer;
import com.stockvision.technicalanalysis.domain.IndicatorType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service managing user-owned price and indicator threshold alert rules.
 */
@Service
public class AlertService {

    private final AlertRuleRepository alertRuleRepository;
    private final InstrumentRepository instrumentRepository;
    private final MarketDataProvider marketDataProvider;
    private final FreshnessEvaluator freshnessEvaluator;
    private final AlertProperties alertProperties;
    private final Clock clock;

    public AlertService(
            AlertRuleRepository alertRuleRepository,
            InstrumentRepository instrumentRepository,
            MarketDataProvider marketDataProvider,
            FreshnessEvaluator freshnessEvaluator,
            AlertProperties alertProperties,
            Clock clock) {
        this.alertRuleRepository = alertRuleRepository;
        this.instrumentRepository = instrumentRepository;
        this.marketDataProvider = marketDataProvider;
        this.freshnessEvaluator = freshnessEvaluator;
        this.alertProperties = alertProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AlertRuleListResponse listAlerts(UUID userId) {
        List<AlertRule> rules = alertRuleRepository.findAllByUserId(userId);
        Set<UUID> instrumentIds =
                rules.stream().map(AlertRule::instrumentId).collect(Collectors.toSet());
        Map<UUID, Instrument> instrumentsById =
                instrumentRepository.findAllByIds(instrumentIds).stream()
                        .collect(Collectors.toMap(Instrument::id, Function.identity()));

        Instant latestEvaluated =
                rules.stream()
                        .map(AlertRule::lastEvaluatedAt)
                        .filter(ts -> ts != null)
                        .max(Instant::compareTo)
                        .orElse(null);

        ProviderMetadata metadata = marketDataProvider.getProviderMetadata();
        FreshnessMetadataResponse freshness =
                freshnessEvaluator.evaluate(metadata, latestEvaluated, Optional.empty());

        List<AlertRuleResponse> responses =
                rules.stream()
                        .map(rule -> toResponse(rule, instrumentsById.get(rule.instrumentId())))
                        .toList();

        return new AlertRuleListResponse(
                responses, DataSourceMetadataResponse.from(metadata), freshness);
    }

    @Transactional
    public AlertRuleResponse createAlert(UUID userId, CreateAlertRequest request) {
        String normalizedSymbol = normalizeSymbol(request.symbol());
        Instrument instrument =
                instrumentRepository
                        .findBySymbol(normalizedSymbol)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.NOT_FOUND,
                                                "Instrument not found: " + normalizedSymbol));

        AlertConditionType conditionType = request.conditionType();
        IndicatorType indicatorType = null;
        String indicatorField = null;

        if (conditionType.isIndicatorCondition()) {
            if (request.indicatorType() == null) {
                throw new AppException(
                        ErrorCode.VALIDATION_ERROR,
                        "indicatorType is required when conditionType is " + conditionType);
            }
            indicatorType = request.indicatorType();
            try {
                indicatorField =
                        AlertIndicatorFieldCatalog.resolveAndValidateField(
                                indicatorType, request.indicatorField());
            } catch (IllegalArgumentException ex) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, ex.getMessage(), ex);
            }
        } else {
            if (request.indicatorType() != null
                    || (request.indicatorField() != null && !request.indicatorField().isBlank())) {
                throw new AppException(
                        ErrorCode.VALIDATION_ERROR,
                        "indicatorType and indicatorField must be omitted for price alerts");
            }
        }

        BarInterval barInterval =
                request.interval() != null && !request.interval().isBlank()
                        ? parseInterval(request.interval())
                        : BarInterval.ONE_DAY;

        BigDecimal thresholdValue = FinancialMath.scaleIndicator(request.thresholdValue());
        if (!conditionType.isIndicatorCondition()
                && thresholdValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR, "Price alert thresholdValue must be > 0");
        }

        int cooldownMinutes =
                request.cooldownMinutes() != null
                        ? request.cooldownMinutes()
                        : alertProperties.getDefaultCooldownMinutes();

        Instant now = clock.instant();
        AlertRule rule =
                new AlertRule(
                        UUID.randomUUID(),
                        userId,
                        instrument.id(),
                        conditionType,
                        indicatorType,
                        indicatorField,
                        barInterval,
                        thresholdValue,
                        cooldownMinutes,
                        AlertStatus.ACTIVE,
                        AlertTriggerState.ARMED,
                        null,
                        null,
                        null,
                        normalizeOptionalText(request.note()),
                        now,
                        now);

        AlertRule saved = alertRuleRepository.save(rule);
        return toResponse(saved, instrument);
    }

    @Transactional
    public AlertRuleResponse updateAlert(UUID userId, UUID alertId, UpdateAlertRequest request) {
        AlertRule existing =
                alertRuleRepository
                        .findByIdAndUserId(alertId, userId)
                        .orElseThrow(
                                () -> new AppException(ErrorCode.NOT_FOUND, "Alert rule not found"));

        AlertStatus nextStatus = request.status() != null ? request.status() : existing.status();
        BigDecimal nextThreshold =
                request.thresholdValue() != null
                        ? FinancialMath.scaleIndicator(request.thresholdValue())
                        : existing.thresholdValue();
        if (!existing.conditionType().isIndicatorCondition()
                && nextThreshold.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR, "Price alert thresholdValue must be > 0");
        }

        int nextCooldown =
                request.cooldownMinutes() != null
                        ? request.cooldownMinutes()
                        : existing.cooldownMinutes();
        String nextNote =
                request.note() != null ? normalizeOptionalText(request.note()) : existing.note();

        boolean thresholdChanged = nextThreshold.compareTo(existing.thresholdValue()) != 0;
        boolean resumedFromPaused =
                existing.status() == AlertStatus.PAUSED && nextStatus == AlertStatus.ACTIVE;
        AlertTriggerState nextTriggerState =
                thresholdChanged || resumedFromPaused
                        ? AlertTriggerState.ARMED
                        : existing.triggerState();

        AlertRule updated =
                new AlertRule(
                        existing.id(),
                        existing.userId(),
                        existing.instrumentId(),
                        existing.conditionType(),
                        existing.indicatorType(),
                        existing.indicatorField(),
                        existing.barInterval(),
                        nextThreshold,
                        nextCooldown,
                        nextStatus,
                        nextTriggerState,
                        existing.lastEvaluatedAt(),
                        existing.lastTriggeredAt(),
                        existing.lastObservedValue(),
                        nextNote,
                        existing.createdAt(),
                        clock.instant());

        AlertRule saved = alertRuleRepository.save(updated);
        Instrument instrument =
                instrumentRepository.findById(saved.instrumentId()).orElse(null);
        return toResponse(saved, instrument);
    }

    @Transactional
    public void deleteAlert(UUID userId, UUID alertId) {
        AlertRule existing =
                alertRuleRepository
                        .findByIdAndUserId(alertId, userId)
                        .orElseThrow(
                                () -> new AppException(ErrorCode.NOT_FOUND, "Alert rule not found"));
        alertRuleRepository.deleteByIdAndUserId(existing.id(), userId);
    }

    private static AlertRuleResponse toResponse(AlertRule rule, Instrument instrument) {
        return new AlertRuleResponse(
                rule.id(),
                rule.instrumentId(),
                instrument != null ? instrument.symbol() : "UNKNOWN",
                instrument != null ? instrument.name() : "Unknown Instrument",
                rule.conditionType(),
                rule.indicatorType(),
                rule.indicatorField(),
                rule.barInterval().code(),
                rule.thresholdValue(),
                rule.cooldownMinutes(),
                rule.status(),
                rule.triggerState(),
                rule.lastEvaluatedAt(),
                rule.lastTriggeredAt(),
                rule.lastObservedValue(),
                rule.note(),
                rule.createdAt(),
                rule.updatedAt());
    }

    private static String normalizeSymbol(String rawSymbol) {
        try {
            return SymbolNormalizer.normalize(rawSymbol);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
    }

    private static BarInterval parseInterval(String rawInterval) {
        try {
            return BarInterval.fromCode(rawInterval);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
    }

    private static String normalizeOptionalText(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }
}
