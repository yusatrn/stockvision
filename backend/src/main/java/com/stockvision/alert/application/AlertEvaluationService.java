package com.stockvision.alert.application;

import com.stockvision.alert.api.AlertEvaluationRunResponse;
import com.stockvision.alert.api.AlertEvaluationRunResponse.EvaluatedRuleResult;
import com.stockvision.alert.domain.AlertConditionType;
import com.stockvision.alert.domain.AlertEvaluationDecision;
import com.stockvision.alert.domain.AlertEvaluationPolicy;
import com.stockvision.alert.domain.AlertNotification;
import com.stockvision.alert.domain.AlertNotificationRepository;
import com.stockvision.alert.domain.AlertRule;
import com.stockvision.alert.domain.AlertRuleRepository;
import com.stockvision.alert.domain.EvaluationOutcomeType;
import com.stockvision.alert.infrastructure.AlertProperties;
import com.stockvision.common.domain.FinancialMath;
import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import com.stockvision.marketdata.api.FreshnessMetadataResponse;
import com.stockvision.marketdata.application.FreshnessEvaluator;
import com.stockvision.marketdata.application.MarketDataIngestionService;
import com.stockvision.marketdata.application.MarketDataIngestionService.DateRange;
import com.stockvision.marketdata.domain.LatestQuote;
import com.stockvision.marketdata.domain.MarketDataBarRepository;
import com.stockvision.marketdata.domain.MarketDataProvider;
import com.stockvision.marketdata.domain.MarketDataProviderException;
import com.stockvision.marketdata.domain.OhlcvBar;
import com.stockvision.marketdata.domain.ProviderMetadata;
import com.stockvision.marketdata.domain.ProviderSyncRun;
import com.stockvision.marketdata.domain.ProviderSyncRunRepository;
import com.stockvision.stock.domain.Instrument;
import com.stockvision.stock.domain.InstrumentRepository;
import com.stockvision.technicalanalysis.domain.IndicatorConfiguration;
import com.stockvision.technicalanalysis.domain.IndicatorSeries;
import com.stockvision.technicalanalysis.domain.IndicatorStatus;
import com.stockvision.technicalanalysis.domain.TechnicalAnalysisResult;
import com.stockvision.technicalanalysis.domain.TechnicalIndicatorService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Evaluates active alert rules against latest market quotes and stateless technical indicators
 * using cooldown state and idempotent notification keys to prevent notification storms.
 */
@Service
public class AlertEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AlertEvaluationService.class);

    private final AlertRuleRepository alertRuleRepository;
    private final AlertNotificationRepository alertNotificationRepository;
    private final InstrumentRepository instrumentRepository;
    private final MarketDataProvider marketDataProvider;
    private final MarketDataBarRepository marketDataBarRepository;
    private final ProviderSyncRunRepository providerSyncRunRepository;
    private final MarketDataIngestionService ingestionService;
    private final FreshnessEvaluator freshnessEvaluator;
    private final TechnicalIndicatorService technicalIndicatorService;
    private final AlertProperties alertProperties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public AlertEvaluationService(
            AlertRuleRepository alertRuleRepository,
            AlertNotificationRepository alertNotificationRepository,
            InstrumentRepository instrumentRepository,
            MarketDataProvider marketDataProvider,
            MarketDataBarRepository marketDataBarRepository,
            ProviderSyncRunRepository providerSyncRunRepository,
            MarketDataIngestionService ingestionService,
            FreshnessEvaluator freshnessEvaluator,
            TechnicalIndicatorService technicalIndicatorService,
            AlertProperties alertProperties,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.alertRuleRepository = alertRuleRepository;
        this.alertNotificationRepository = alertNotificationRepository;
        this.instrumentRepository = instrumentRepository;
        this.marketDataProvider = marketDataProvider;
        this.marketDataBarRepository = marketDataBarRepository;
        this.providerSyncRunRepository = providerSyncRunRepository;
        this.ingestionService = ingestionService;
        this.freshnessEvaluator = freshnessEvaluator;
        this.technicalIndicatorService = technicalIndicatorService;
        this.alertProperties = alertProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public AlertEvaluationRunResponse evaluateAllActiveRules() {
        List<AlertRule> rules = alertRuleRepository.findAllActive();
        return evaluateRules(rules);
    }

    public AlertEvaluationRunResponse evaluateActiveRulesForUser(UUID userId) {
        List<AlertRule> rules = alertRuleRepository.findAllActiveByUserId(userId);
        return evaluateRules(rules);
    }

    private AlertEvaluationRunResponse evaluateRules(List<AlertRule> rules) {
        ProviderMetadata metadata = marketDataProvider.getProviderMetadata();
        Instant evaluatedAt = clock.instant();

        Set<UUID> instrumentIds =
                rules.stream().map(AlertRule::instrumentId).collect(Collectors.toSet());
        Map<UUID, Instrument> instrumentsById =
                instrumentRepository.findAllByIds(instrumentIds).stream()
                        .collect(Collectors.toMap(Instrument::id, Function.identity()));

        List<EvaluatedRuleResult> results = new ArrayList<>(rules.size());
        int triggeredCount = 0;
        int suppressedCount = 0;

        for (AlertRule rule : rules) {
            Instrument instrument = instrumentsById.get(rule.instrumentId());
            if (instrument == null) {
                continue;
            }
            EvaluatedRuleResult outcome = evaluateSingleRuleSafely(rule, instrument, metadata);
            results.add(outcome);
            if (outcome.outcome() == EvaluationOutcomeType.TRIGGERED) {
                triggeredCount++;
            } else if (outcome.outcome() == EvaluationOutcomeType.COOLDOWN_SUPPRESSED
                    || outcome.outcome() == EvaluationOutcomeType.DUPLICATE_SUPPRESSED) {
                suppressedCount++;
            }
        }

        return new AlertEvaluationRunResponse(
                results.size(),
                triggeredCount,
                suppressedCount,
                evaluatedAt,
                DataSourceMetadataResponse.from(metadata),
                results);
    }

    private EvaluatedRuleResult evaluateSingleRuleSafely(
            AlertRule rule, Instrument instrument, ProviderMetadata metadata) {
        Instant now = clock.instant();
        Observation observation = observeRuleInput(rule, instrument, metadata);

        AlertEvaluationDecision decision =
                AlertEvaluationPolicy.evaluate(
                        rule,
                        observation.observedValue(),
                        observation.dataTimestamp(),
                        observation.freshness().stale(),
                        alertProperties.isRejectStaleData(),
                        now);

        if (decision.outcome() == EvaluationOutcomeType.TRIGGERED
                && alertNotificationRepository.existsByIdempotencyKey(decision.idempotencyKey())) {
            updateRuleOnly(rule, observation.observedValue(), decision.nextTriggerState(), now, rule.lastTriggeredAt());
            return new EvaluatedRuleResult(
                    rule.id(),
                    instrument.symbol(),
                    EvaluationOutcomeType.DUPLICATE_SUPPRESSED,
                    observation.observedValue(),
                    "Notification already exists for idempotency key in current evaluation window.");
        }

        try {
            transactionTemplate.executeWithoutResult(
                    status -> {
                        if (decision.outcome() == EvaluationOutcomeType.TRIGGERED) {
                            AlertNotification notification =
                                    buildNotification(
                                            rule,
                                            instrument,
                                            metadata,
                                            observation,
                                            decision.idempotencyKey(),
                                            now);
                            alertNotificationRepository.save(notification);
                            alertRuleRepository.save(
                                    withUpdatedEvaluation(
                                            rule,
                                            decision.nextTriggerState(),
                                            now,
                                            now,
                                            observation.observedValue()));
                        } else {
                            alertRuleRepository.save(
                                    withUpdatedEvaluation(
                                            rule,
                                            decision.nextTriggerState(),
                                            now,
                                            rule.lastTriggeredAt(),
                                            observation.observedValue() != null
                                                    ? observation.observedValue()
                                                    : rule.lastObservedValue()));
                        }
                    });

            return new EvaluatedRuleResult(
                    rule.id(),
                    instrument.symbol(),
                    decision.outcome(),
                    observation.observedValue(),
                    decision.reason());
        } catch (DataIntegrityViolationException duplicateEx) {
            updateRuleOnly(rule, observation.observedValue(), decision.nextTriggerState(), now, rule.lastTriggeredAt());
            return new EvaluatedRuleResult(
                    rule.id(),
                    instrument.symbol(),
                    EvaluationOutcomeType.DUPLICATE_SUPPRESSED,
                    observation.observedValue(),
                    "Duplicate notification prevented by unique idempotency constraint.");
        }
    }

    private void updateRuleOnly(
            AlertRule rule,
            BigDecimal observedValue,
            com.stockvision.alert.domain.AlertTriggerState nextTriggerState,
            Instant evaluatedAt,
            Instant lastTriggeredAt) {
        transactionTemplate.executeWithoutResult(
                status ->
                        alertRuleRepository.save(
                                withUpdatedEvaluation(
                                        rule,
                                        nextTriggerState,
                                        evaluatedAt,
                                        lastTriggeredAt,
                                        observedValue != null
                                                ? observedValue
                                                : rule.lastObservedValue())));
    }

    private Observation observeRuleInput(
            AlertRule rule, Instrument instrument, ProviderMetadata metadata) {
        if (!rule.conditionType().isIndicatorCondition()) {
            return observePriceRule(instrument, metadata);
        }
        return observeIndicatorRule(rule, instrument, metadata);
    }

    private Observation observePriceRule(Instrument instrument, ProviderMetadata metadata) {
        try {
            LatestQuote quote = marketDataProvider.getLatestQuote(instrument.symbol());
            Optional<ProviderSyncRun> latestSync = Optional.empty();
            FreshnessMetadataResponse freshness =
                    freshnessEvaluator.evaluate(metadata, quote.quotedAt(), latestSync);
            return new Observation(
                    FinancialMath.scaleIndicator(quote.price()),
                    quote.quotedAt(),
                    freshness);
        } catch (MarketDataProviderException | IllegalStateException ex) {
            log.warn("Price alert observation failed for {}: {}", instrument.symbol(), ex.getMessage());
            FreshnessMetadataResponse freshness =
                    freshnessEvaluator.evaluate(metadata, null, Optional.empty());
            return new Observation(null, null, freshness);
        }
    }

    private Observation observeIndicatorRule(
            AlertRule rule, Instrument instrument, ProviderMetadata metadata) {
        DateRange range = ingestionService.resolveAndValidateRange(rule.barInterval(), null, null);
        List<OhlcvBar> bars =
                marketDataBarRepository.findBars(
                        instrument.id(),
                        rule.barInterval(),
                        metadata.providerId(),
                        false,
                        range.from(),
                        range.to());

        if (bars.isEmpty()) {
            try {
                ingestionService.ingestBars(
                        instrument.symbol(), rule.barInterval(), range.from(), range.to(), false);
                bars =
                        marketDataBarRepository.findBars(
                                instrument.id(),
                                rule.barInterval(),
                                metadata.providerId(),
                                false,
                                range.from(),
                                range.to());
            } catch (RuntimeException ex) {
                log.warn(
                        "On-demand bar ingestion for indicator alert failed ({}): {}",
                        instrument.symbol(),
                        ex.getMessage());
            }
        }

        Instant latestBarTimestamp = bars.isEmpty() ? null : bars.getLast().timestamp();
        Optional<ProviderSyncRun> latestSync =
                providerSyncRunRepository.findLatestByInstrumentAndInterval(
                        metadata.providerId(), instrument.id(), rule.barInterval(), false);
        FreshnessMetadataResponse freshness =
                freshnessEvaluator.evaluate(metadata, latestBarTimestamp, latestSync);

        if (bars.isEmpty()) {
            return new Observation(null, null, freshness);
        }

        TechnicalAnalysisResult result =
                technicalIndicatorService.calculate(
                        bars, List.of(rule.indicatorType()), IndicatorConfiguration.defaults());
        IndicatorSeries series =
                result.series().stream()
                        .filter(s -> s.type() == rule.indicatorType())
                        .findFirst()
                        .orElse(null);
        if (series == null || series.status() != IndicatorStatus.READY) {
            return new Observation(null, latestBarTimestamp, freshness);
        }

        BigDecimal value = series.latestValues().get(rule.indicatorField());
        if (value == null) {
            return new Observation(null, latestBarTimestamp, freshness);
        }

        return new Observation(FinancialMath.scaleIndicator(value), latestBarTimestamp, freshness);
    }

    private static AlertRule withUpdatedEvaluation(
            AlertRule rule,
            com.stockvision.alert.domain.AlertTriggerState triggerState,
            Instant evaluatedAt,
            Instant triggeredAt,
            BigDecimal observedValue) {
        return new AlertRule(
                rule.id(),
                rule.userId(),
                rule.instrumentId(),
                rule.conditionType(),
                rule.indicatorType(),
                rule.indicatorField(),
                rule.barInterval(),
                rule.thresholdValue(),
                rule.cooldownMinutes(),
                rule.status(),
                triggerState,
                evaluatedAt,
                triggeredAt,
                observedValue,
                rule.note(),
                rule.createdAt(),
                evaluatedAt);
    }

    private static AlertNotification buildNotification(
            AlertRule rule,
            Instrument instrument,
            ProviderMetadata metadata,
            Observation observation,
            String idempotencyKey,
            Instant now) {
        String conditionLabel = formatConditionDescription(rule, observation.observedValue());
        String sourceTag =
                metadata.simulated()
                        ? metadata.displayName() + " [SİMÜLE]"
                        : metadata.displayName() + " [" + metadata.mode().name() + "]";
        String message =
                instrument.symbol()
                        + " alarmı tetiklendi: "
                        + conditionLabel
                        + " (Kaynak: "
                        + sourceTag
                        + ", Tazelik: "
                        + observation.freshness().status().name()
                        + ").";

        return new AlertNotification(
                UUID.randomUUID(),
                rule.id(),
                rule.userId(),
                instrument.id(),
                instrument.symbol(),
                rule.conditionType(),
                rule.indicatorType(),
                rule.indicatorField(),
                rule.thresholdValue(),
                observation.observedValue(),
                observation.dataTimestamp(),
                metadata.providerId(),
                metadata.displayName(),
                metadata.mode(),
                metadata.simulated(),
                observation.freshness().status(),
                observation.freshness().stale(),
                message,
                idempotencyKey,
                null,
                now);
    }

    private static String formatConditionDescription(AlertRule rule, BigDecimal observedValue) {
        String comparator = rule.conditionType().isAbove() ? ">=" : "<=";
        String observedStr = observedValue.stripTrailingZeros().toPlainString();
        String thresholdStr = rule.thresholdValue().stripTrailingZeros().toPlainString();
        if (rule.conditionType().isIndicatorCondition()) {
            return rule.indicatorType().name()
                    + "."
                    + rule.indicatorField()
                    + " ("
                    + rule.barInterval().code()
                    + ") gözlenen değer "
                    + observedStr
                    + " "
                    + comparator
                    + " eşik "
                    + thresholdStr;
        }
        return "Fiyat gözlenen değer " + observedStr + " " + comparator + " eşik " + thresholdStr;
    }

    private record Observation(
            BigDecimal observedValue,
            Instant dataTimestamp,
            FreshnessMetadataResponse freshness) {
    }
}
