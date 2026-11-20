package com.stockvision.ai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockvision.ai.api.AiExplanationResponse;
import com.stockvision.ai.domain.AiExplanation;
import com.stockvision.ai.domain.AiExplanationProvider;
import com.stockvision.ai.domain.AiExplanationRepository;
import com.stockvision.ai.domain.AiExplanationStatus;
import com.stockvision.ai.domain.AiFactPack;
import com.stockvision.ai.domain.AiFactPack.FactComponent;
import com.stockvision.ai.domain.AiFallbackReason;
import com.stockvision.ai.domain.AiOutputValidationResult;
import com.stockvision.ai.domain.AiOutputValidator;
import com.stockvision.ai.domain.AiPromptBuilder;
import com.stockvision.ai.domain.AiProviderException;
import com.stockvision.ai.domain.AiRawGenerationResult;
import com.stockvision.ai.domain.DeterministicTemplateExplanationGenerator;
import com.stockvision.ai.domain.StructuredAiExplanation;
import com.stockvision.ai.infrastructure.DeterministicTemplateAiProvider;
import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import com.stockvision.marketdata.api.FreshnessMetadataResponse;
import com.stockvision.marketdata.application.FreshnessEvaluator;
import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.marketdata.domain.MarketDataProvider;
import com.stockvision.marketdata.domain.ProviderMetadata;
import com.stockvision.marketdata.domain.ProviderSyncRun;
import com.stockvision.marketdata.domain.ProviderSyncRunRepository;
import com.stockvision.scoring.api.InstrumentAnalysisResponse;
import com.stockvision.scoring.application.ScoringApplicationService;
import com.stockvision.scoring.domain.DeterministicTechnicalScoringEngine;
import com.stockvision.scoring.domain.ScoreComponentResult;
import com.stockvision.scoring.domain.ScoreExplanation;
import com.stockvision.scoring.domain.ScoreStatus;
import com.stockvision.scoring.domain.TechnicalScoreRun;
import com.stockvision.scoring.domain.TechnicalScoreRunRepository;
import com.stockvision.stock.domain.Instrument;
import com.stockvision.stock.domain.InstrumentRepository;
import com.stockvision.stock.domain.SymbolNormalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates fact-only AI explanation generation, schema/guardrail verification, deterministic
 * template fallback, and audit persistence without ever forwarding user PII or personal portfolios.
 */
@Service
public class AiExplanationService {

    private static final Logger log = LoggerFactory.getLogger(AiExplanationService.class);

    public static final String MANDATORY_DISCLAIMER =
            "Yalnızca bilgilendirme amaçlı karar destek özetidir. Yapay zekâ veya şablon metinleri hata ya da eksiklik içerebilir, "
                    + "modellenmemiş haberleri ve şirket temellerini kapsamaz; kişiye özel yatırım danışmanlığı değildir.";

    private static final TypeReference<List<ScoreComponentResult>> COMPONENT_LIST_TYPE =
            new TypeReference<>() {};

    private final InstrumentRepository instrumentRepository;
    private final MarketDataProvider marketDataProvider;
    private final ProviderSyncRunRepository providerSyncRunRepository;
    private final FreshnessEvaluator freshnessEvaluator;
    private final TechnicalScoreRunRepository scoreRunRepository;
    private final ScoringApplicationService scoringApplicationService;
    private final AiExplanationRepository aiExplanationRepository;
    private final AiExplanationProvider configuredProvider;
    private final AiPromptBuilder promptBuilder;
    private final AiOutputValidator outputValidator;
    private final DeterministicTemplateExplanationGenerator templateGenerator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AiExplanationService(
            InstrumentRepository instrumentRepository,
            MarketDataProvider marketDataProvider,
            ProviderSyncRunRepository providerSyncRunRepository,
            FreshnessEvaluator freshnessEvaluator,
            TechnicalScoreRunRepository scoreRunRepository,
            ScoringApplicationService scoringApplicationService,
            AiExplanationRepository aiExplanationRepository,
            AiExplanationProvider configuredProvider,
            AiPromptBuilder promptBuilder,
            AiOutputValidator outputValidator,
            DeterministicTemplateExplanationGenerator templateGenerator,
            ObjectMapper objectMapper,
            Clock clock) {
        this.instrumentRepository = instrumentRepository;
        this.marketDataProvider = marketDataProvider;
        this.providerSyncRunRepository = providerSyncRunRepository;
        this.freshnessEvaluator = freshnessEvaluator;
        this.scoreRunRepository = scoreRunRepository;
        this.scoringApplicationService = scoringApplicationService;
        this.aiExplanationRepository = aiExplanationRepository;
        this.configuredProvider = configuredProvider;
        this.promptBuilder = promptBuilder;
        this.outputValidator = outputValidator;
        this.templateGenerator = templateGenerator;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public AiExplanationResponse getOrGenerateExplanation(
            String rawSymbol,
            String rawInterval,
            boolean adjusted,
            UUID requestedScoreRunId,
            boolean forceRegenerate) {
        String normalizedSymbol = normalizeSymbol(rawSymbol);
        BarInterval interval = parseInterval(rawInterval);

        Instrument instrument =
                instrumentRepository
                        .findBySymbol(normalizedSymbol)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.NOT_FOUND,
                                                "Instrument not found: " + normalizedSymbol));

        ProviderMetadata metadata = marketDataProvider.getProviderMetadata();
        TechnicalScoreRun scoreRun =
                resolveScoreRun(instrument, interval, metadata, adjusted, requestedScoreRunId);

        Optional<ProviderSyncRun> latestSync =
                providerSyncRunRepository.findLatestByInstrumentAndInterval(
                        metadata.providerId(), instrument.id(), interval, scoreRun.adjusted());
        FreshnessMetadataResponse freshness =
                freshnessEvaluator.evaluate(metadata, scoreRun.latestBarTimestamp(), latestSync);
        DataSourceMetadataResponse dataSource = DataSourceMetadataResponse.from(metadata);

        String requestedProviderId = configuredProvider.providerId();
        if (!forceRegenerate) {
            Optional<AiExplanation> cached =
                    aiExplanationRepository.findLatestByScoreRunAndPromptAndRequestedProvider(
                            scoreRun.id(), AiPromptBuilder.PROMPT_VERSION, requestedProviderId);
            if (cached.isPresent()) {
                Optional<StructuredAiExplanation> parsed =
                        parseStructuredOutput(cached.get().structuredOutputJson());
                if (parsed.isPresent()) {
                    return toResponse(
                            cached.get(), scoreRun.adjusted(), parsed.get(), dataSource, freshness);
                }
            }
        }

        ParsedFacts parsedFacts = extractFacts(instrument, scoreRun, metadata, freshness);
        GenerationOutcome outcome = evaluateAndGenerate(scoreRun, parsedFacts);

        AiExplanation toPersist =
                new AiExplanation(
                        UUID.randomUUID(),
                        scoreRun.analysisRunId(),
                        scoreRun.id(),
                        instrument.id(),
                        instrument.symbol(),
                        scoreRun.interval(),
                        outcome.providerId(),
                        requestedProviderId,
                        outcome.modelName(),
                        AiPromptBuilder.PROMPT_VERSION,
                        outcome.status(),
                        outcome.fallbackUsed(),
                        outcome.fallbackReason(),
                        scoreRun.simulated() || metadata.simulated(),
                        scoreRun.staleData() || freshness.stale(),
                        serializeStructuredOutput(outcome.explanation()),
                        MANDATORY_DISCLAIMER,
                        clock.instant());

        AiExplanation saved = aiExplanationRepository.save(toPersist);
        return toResponse(
                saved, scoreRun.adjusted(), outcome.explanation(), dataSource, freshness);
    }

    private TechnicalScoreRun resolveScoreRun(
            Instrument instrument,
            BarInterval interval,
            ProviderMetadata metadata,
            boolean adjusted,
            UUID requestedScoreRunId) {
        if (requestedScoreRunId != null) {
            TechnicalScoreRun run =
                    scoreRunRepository
                            .findById(requestedScoreRunId)
                            .orElseThrow(
                                    () ->
                                            new AppException(
                                                    ErrorCode.NOT_FOUND,
                                                    "Score run not found: " + requestedScoreRunId));
            if (!run.instrumentId().equals(instrument.id())) {
                throw new AppException(
                        ErrorCode.NOT_FOUND,
                        "Score run does not belong to instrument " + instrument.symbol());
            }
            return run;
        }

        Optional<TechnicalScoreRun> existing =
                scoreRunRepository.findLatestMatching(
                        instrument.id(),
                        interval,
                        metadata.providerId(),
                        adjusted,
                        DeterministicTechnicalScoringEngine.ALGORITHM_VERSION);
        if (existing.isPresent()) {
            return existing.get();
        }

        InstrumentAnalysisResponse computed =
                scoringApplicationService.analyzeInstrument(
                        instrument.symbol(), interval.code(), null, null, adjusted);
        return scoreRunRepository
                .findById(computed.scoreRunId())
                .orElseThrow(
                        () ->
                                new AppException(
                                        ErrorCode.INTERNAL_ERROR,
                                        "Computed score run could not be reloaded"));
    }

    private GenerationOutcome evaluateAndGenerate(
            TechnicalScoreRun scoreRun, ParsedFacts parsedFacts) {
        boolean templateMode =
                DeterministicTemplateAiProvider.PROVIDER_ID.equals(configuredProvider.providerId());

        if (scoreRun.status() == ScoreStatus.INSUFFICIENT_DATA
                || scoreRun.staleData()
                || parsedFacts.factsCorruptOrMissing()) {
            StructuredAiExplanation templateOut = templateGenerator.generate(parsedFacts.factPack());
            return new GenerationOutcome(
                    AiExplanationStatus.INSUFFICIENT_DATA,
                    !templateMode,
                    templateMode
                            ? AiFallbackReason.TEMPLATE_MODE_SELECTED
                            : AiFallbackReason.INSUFFICIENT_DATA_SHORT_CIRCUIT,
                    DeterministicTemplateAiProvider.PROVIDER_ID,
                    DeterministicTemplateAiProvider.MODEL_NAME,
                    templateOut);
        }

        if (templateMode) {
            return new GenerationOutcome(
                    AiExplanationStatus.GENERATED,
                    false,
                    AiFallbackReason.TEMPLATE_MODE_SELECTED,
                    DeterministicTemplateAiProvider.PROVIDER_ID,
                    DeterministicTemplateAiProvider.MODEL_NAME,
                    templateGenerator.generate(parsedFacts.factPack()));
        }

        if (!configuredProvider.isConfigured()) {
            log.info(
                    "AI provider {} is unconfigured; serving deterministic template fallback for {}",
                    configuredProvider.providerId(),
                    scoreRun.symbol());
            return new GenerationOutcome(
                    AiExplanationStatus.FALLBACK,
                    true,
                    AiFallbackReason.PROVIDER_UNCONFIGURED,
                    DeterministicTemplateAiProvider.PROVIDER_ID,
                    DeterministicTemplateAiProvider.MODEL_NAME,
                    templateGenerator.generate(parsedFacts.factPack()));
        }

        try {
            AiRawGenerationResult raw =
                    configuredProvider.generate(promptBuilder.build(parsedFacts.factPack()));
            AiOutputValidationResult validation = outputValidator.validate(raw.rawText());
            if (validation.valid()) {
                return new GenerationOutcome(
                        AiExplanationStatus.GENERATED,
                        false,
                        null,
                        raw.providerId(),
                        raw.modelName(),
                        validation.explanation());
            }

            log.warn(
                    "AI provider {} output rejected for {} (reason={}): {}",
                    configuredProvider.providerId(),
                    scoreRun.symbol(),
                    validation.rejectionReason(),
                    validation.detail());
            return new GenerationOutcome(
                    AiExplanationStatus.FALLBACK,
                    true,
                    validation.rejectionReason(),
                    DeterministicTemplateAiProvider.PROVIDER_ID,
                    DeterministicTemplateAiProvider.MODEL_NAME,
                    templateGenerator.generate(parsedFacts.factPack()));
        } catch (AiProviderException ex) {
            AiFallbackReason reason =
                    switch (ex.getErrorType()) {
                        case UNCONFIGURED -> AiFallbackReason.PROVIDER_UNCONFIGURED;
                        case TIMEOUT -> AiFallbackReason.PROVIDER_TIMEOUT;
                        case UNAVAILABLE -> AiFallbackReason.PROVIDER_UNAVAILABLE;
                    };
            log.warn(
                    "AI provider {} failed for {} (reason={}): {}",
                    configuredProvider.providerId(),
                    scoreRun.symbol(),
                    reason,
                    ex.getMessage());
            return new GenerationOutcome(
                    AiExplanationStatus.FALLBACK,
                    true,
                    reason,
                    DeterministicTemplateAiProvider.PROVIDER_ID,
                    DeterministicTemplateAiProvider.MODEL_NAME,
                    templateGenerator.generate(parsedFacts.factPack()));
        } catch (RuntimeException ex) {
            log.warn(
                    "Unexpected AI provider failure for {}: {}",
                    scoreRun.symbol(),
                    ex.getMessage());
            return new GenerationOutcome(
                    AiExplanationStatus.FALLBACK,
                    true,
                    AiFallbackReason.PROVIDER_UNAVAILABLE,
                    DeterministicTemplateAiProvider.PROVIDER_ID,
                    DeterministicTemplateAiProvider.MODEL_NAME,
                    templateGenerator.generate(parsedFacts.factPack()));
        }
    }

    private ParsedFacts extractFacts(
            Instrument instrument,
            TechnicalScoreRun scoreRun,
            ProviderMetadata metadata,
            FreshnessMetadataResponse freshness) {
        boolean corrupt = false;
        List<FactComponent> factComponents = List.of();
        try {
            List<ScoreComponentResult> decoded =
                    objectMapper.readValue(scoreRun.componentsJson(), COMPONENT_LIST_TYPE);
            factComponents =
                    decoded.stream()
                            .map(
                                    c ->
                                            new FactComponent(
                                                    c.type().name(),
                                                    c.status().name(),
                                                    c.weightPercent(),
                                                    c.normalizedScore(),
                                                    c.weightedContribution(),
                                                    c.explanation(),
                                                    c.metrics()))
                            .toList();
            if (factComponents.isEmpty()) {
                corrupt = true;
            }
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn(
                    "Unable to parse persisted components_json for scoreRun {}: {}",
                    scoreRun.id(),
                    ex.getMessage());
            corrupt = true;
        }

        String headline = null;
        List<String> keyDrivers = List.of();
        List<String> riskNotes = List.of();
        try {
            ScoreExplanation decodedExp =
                    objectMapper.readValue(scoreRun.explanationJson(), ScoreExplanation.class);
            headline = decodedExp.headline();
            keyDrivers = decodedExp.keyDrivers();
            riskNotes = decodedExp.riskAndDataNotes();
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn(
                    "Unable to parse persisted explanation_json for scoreRun {}: {}",
                    scoreRun.id(),
                    ex.getMessage());
            corrupt = true;
        }

        AiFactPack pack =
                new AiFactPack(
                        scoreRun.analysisRunId(),
                        scoreRun.id(),
                        instrument.symbol(),
                        instrument.name(),
                        instrument.exchangeCode(),
                        instrument.sector(),
                        instrument.currency(),
                        scoreRun.interval().code(),
                        scoreRun.adjusted(),
                        scoreRun.algorithmVersion(),
                        scoreRun.status(),
                        scoreRun.staleData() || freshness.stale(),
                        scoreRun.finalScore(),
                        scoreRun.regime(),
                        scoreRun.trendDirection(),
                        scoreRun.latestClose(),
                        scoreRun.latestRsi(),
                        scoreRun.latestBarTimestamp(),
                        scoreRun.calculatedAt(),
                        metadata.displayName(),
                        metadata.mode(),
                        scoreRun.simulated() || metadata.simulated(),
                        metadata.delayMinutes(),
                        freshness.status(),
                        freshness.note(),
                        factComponents,
                        headline,
                        keyDrivers,
                        riskNotes);
        return new ParsedFacts(pack, corrupt);
    }

    private Optional<StructuredAiExplanation> parseStructuredOutput(String json) {
        try {
            StructuredAiExplanation parsed =
                    objectMapper.readValue(json, StructuredAiExplanation.class);
            return Optional.of(parsed);
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    private String serializeStructuredOutput(StructuredAiExplanation explanation) {
        try {
            return objectMapper.writeValueAsString(explanation);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize AI explanation JSON", ex);
        }
    }

    private static AiExplanationResponse toResponse(
            AiExplanation entity,
            boolean adjusted,
            StructuredAiExplanation explanation,
            DataSourceMetadataResponse dataSource,
            FreshnessMetadataResponse freshness) {
        return new AiExplanationResponse(
                entity.id(),
                entity.analysisRunId(),
                entity.scoreRunId(),
                entity.symbol(),
                entity.barInterval().code(),
                adjusted,
                entity.providerId(),
                entity.requestedProviderId(),
                entity.modelName(),
                entity.promptVersion(),
                entity.status(),
                entity.fallbackUsed(),
                entity.fallbackReason(),
                entity.simulatedData(),
                entity.staleData(),
                entity.generatedAt(),
                explanation,
                dataSource,
                freshness,
                entity.disclaimer());
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

    private record ParsedFacts(AiFactPack factPack, boolean factsCorruptOrMissing) {
    }

    private record GenerationOutcome(
            AiExplanationStatus status,
            boolean fallbackUsed,
            AiFallbackReason fallbackReason,
            String providerId,
            String modelName,
            StructuredAiExplanation explanation) {
    }
}
