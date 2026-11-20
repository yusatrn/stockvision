package com.stockvision.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stockvision.ai.api.AiExplanationResponse;
import com.stockvision.ai.domain.AiExplanation;
import com.stockvision.ai.domain.AiExplanationProvider;
import com.stockvision.ai.domain.AiExplanationRepository;
import com.stockvision.ai.domain.AiExplanationStatus;
import com.stockvision.ai.domain.AiFallbackReason;
import com.stockvision.ai.domain.AiOutputValidator;
import com.stockvision.ai.domain.AiPromptBuilder;
import com.stockvision.ai.domain.AiProviderErrorType;
import com.stockvision.ai.domain.AiProviderException;
import com.stockvision.ai.domain.AiRawGenerationResult;
import com.stockvision.ai.domain.DeterministicTemplateExplanationGenerator;
import com.stockvision.ai.infrastructure.DeterministicTemplateAiProvider;
import com.stockvision.marketdata.application.FreshnessEvaluator;
import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.marketdata.domain.DataProviderMode;
import com.stockvision.marketdata.domain.MarketDataProvider;
import com.stockvision.marketdata.domain.ProviderMetadata;
import com.stockvision.marketdata.domain.ProviderSyncRunRepository;
import com.stockvision.marketdata.infrastructure.MarketDataProperties;
import com.stockvision.scoring.application.ScoringApplicationService;
import com.stockvision.scoring.domain.ScoreComponentResult;
import com.stockvision.scoring.domain.ScoreComponentType;
import com.stockvision.scoring.domain.ScoreExplanation;
import com.stockvision.scoring.domain.ScoreRegime;
import com.stockvision.scoring.domain.ScoreStatus;
import com.stockvision.scoring.domain.TechnicalScoreRun;
import com.stockvision.scoring.domain.TechnicalScoreRunRepository;
import com.stockvision.scoring.domain.TrendDirection;
import com.stockvision.stock.domain.Instrument;
import com.stockvision.stock.domain.InstrumentRepository;
import com.stockvision.stock.domain.InstrumentType;
import com.stockvision.technicalanalysis.domain.IndicatorStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiExplanationServiceFallbackTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-20T09:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private ObjectMapper objectMapper;
    private InstrumentRepository instrumentRepository;
    private MarketDataProvider marketDataProvider;
    private ProviderSyncRunRepository providerSyncRunRepository;
    private TechnicalScoreRunRepository scoreRunRepository;
    private ScoringApplicationService scoringApplicationService;
    private AiExplanationRepository aiExplanationRepository;
    private AiExplanationProvider externalProvider;
    private AiExplanationService service;

    private Instrument thyao;
    private UUID scoreRunId;

    @BeforeEach
    void setUp() {
        objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        instrumentRepository = mock(InstrumentRepository.class);
        marketDataProvider = mock(MarketDataProvider.class);
        providerSyncRunRepository = mock(ProviderSyncRunRepository.class);
        scoreRunRepository = mock(TechnicalScoreRunRepository.class);
        scoringApplicationService = mock(ScoringApplicationService.class);
        aiExplanationRepository = mock(AiExplanationRepository.class);
        externalProvider = mock(AiExplanationProvider.class);

        when(externalProvider.providerId()).thenReturn("ollama");
        when(externalProvider.modelName()).thenReturn("llama3.2:3b");
        when(externalProvider.isConfigured()).thenReturn(true);

        thyao =
                new Instrument(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BIST",
                        "THYAO",
                        "Turkish Airlines",
                        "Transportation",
                        "TRY",
                        InstrumentType.EQUITY,
                        true,
                        FIXED_NOW,
                        FIXED_NOW);
        when(instrumentRepository.findBySymbol("THYAO")).thenReturn(Optional.of(thyao));
        when(marketDataProvider.getProviderMetadata())
                .thenReturn(
                        new ProviderMetadata(
                                "mock",
                                "Mock Provider",
                                DataProviderMode.SIMULATED,
                                true,
                                0,
                                "Simulated feed",
                                "Europe/Istanbul"));
        when(providerSyncRunRepository.findLatestByInstrumentAndInterval(any(), any(), any(), any(boolean.class)))
                .thenReturn(Optional.empty());
        when(aiExplanationRepository.findLatestByScoreRunAndPromptAndRequestedProvider(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(aiExplanationRepository.save(any(AiExplanation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        scoreRunId = UUID.randomUUID();
        MarketDataProperties mdProps = new MarketDataProperties();
        service =
                new AiExplanationService(
                        instrumentRepository,
                        marketDataProvider,
                        providerSyncRunRepository,
                        new FreshnessEvaluator(mdProps, FIXED_CLOCK),
                        scoreRunRepository,
                        scoringApplicationService,
                        aiExplanationRepository,
                        externalProvider,
                        new AiPromptBuilder(objectMapper),
                        new AiOutputValidator(objectMapper),
                        new DeterministicTemplateExplanationGenerator(),
                        objectMapper,
                        FIXED_CLOCK);
    }

    @Test
    void fallsBackToDeterministicTemplateWhenProviderTimesOut() throws Exception {
        TechnicalScoreRun readyRun = readyScoreRun();
        when(scoreRunRepository.findById(scoreRunId)).thenReturn(Optional.of(readyRun));
        when(externalProvider.generate(any()))
                .thenThrow(new AiProviderException(AiProviderErrorType.TIMEOUT, "Timed out"));

        AiExplanationResponse res =
                service.getOrGenerateExplanation("THYAO", "1d", false, scoreRunId, false);

        assertThat(res.status()).isEqualTo(AiExplanationStatus.FALLBACK);
        assertThat(res.fallbackUsed()).isTrue();
        assertThat(res.fallbackReason()).isEqualTo(AiFallbackReason.PROVIDER_TIMEOUT);
        assertThat(res.providerId()).isEqualTo(DeterministicTemplateAiProvider.PROVIDER_ID);
        assertThat(res.explanation().observedFacts()).isNotEmpty();
        assertThat(res.disclaimer()).contains("yatırım danışmanlığı değildir");
    }

    @Test
    void fallsBackToDeterministicTemplateOnMalformedOrGuardrailOutput() throws Exception {
        TechnicalScoreRun readyRun = readyScoreRun();
        when(scoreRunRepository.findById(scoreRunId)).thenReturn(Optional.of(readyRun));
        when(externalProvider.generate(any()))
                .thenReturn(new AiRawGenerationResult("not json {{{", "ollama", "llama3.2:3b"));

        AiExplanationResponse malformedRes =
                service.getOrGenerateExplanation("THYAO", "1d", false, scoreRunId, false);
        assertThat(malformedRes.status()).isEqualTo(AiExplanationStatus.FALLBACK);
        assertThat(malformedRes.fallbackReason()).isEqualTo(AiFallbackReason.MALFORMED_OUTPUT);

        when(externalProvider.generate(any()))
                .thenReturn(
                        new AiRawGenerationResult(
                                """
                                {
                                  "summary": "Buy immediately for guaranteed profit on THYAO breakout.",
                                  "observedFacts": ["Close is 298.50 TRY."],
                                  "interpretation": ["Bullish setup."],
                                  "uncertaintiesAndRisks": ["Simulated feed."],
                                  "confidenceNote": "High certainty statement."
                                }
                                """,
                                "ollama",
                                "llama3.2:3b"));

        AiExplanationResponse guardrailRes =
                service.getOrGenerateExplanation("THYAO", "1d", false, scoreRunId, true);
        assertThat(guardrailRes.status()).isEqualTo(AiExplanationStatus.FALLBACK);
        assertThat(guardrailRes.fallbackReason()).isEqualTo(AiFallbackReason.GUARDRAIL_VIOLATION);
    }

    @Test
    void shortCircuitsExternalProviderWhenFactsAreInsufficient() throws Exception {
        TechnicalScoreRun insufficientRun =
                new TechnicalScoreRun(
                        scoreRunId,
                        UUID.randomUUID(),
                        thyao.id(),
                        "THYAO",
                        "BIST",
                        BarInterval.ONE_DAY,
                        "mock",
                        false,
                        true,
                        "score-engine-v1.0.0",
                        ScoreStatus.INSUFFICIENT_DATA,
                        false,
                        null,
                        ScoreRegime.INSUFFICIENT_DATA,
                        TrendDirection.UNKNOWN,
                        null,
                        null,
                        "{}",
                        "[]",
                        objectMapper.writeValueAsString(
                                new ScoreExplanation(
                                        "Insufficient bars",
                                        List.of(),
                                        List.of("Need at least 35 bars"),
                                        "Halted",
                                        "Disclaimer")),
                        FIXED_NOW.minusSeconds(3600),
                        FIXED_NOW,
                        null,
                        FIXED_NOW);
        when(scoreRunRepository.findById(scoreRunId)).thenReturn(Optional.of(insufficientRun));

        AiExplanationResponse res =
                service.getOrGenerateExplanation("THYAO", "1d", false, scoreRunId, false);

        verify(externalProvider, never()).generate(any());
        assertThat(res.status()).isEqualTo(AiExplanationStatus.INSUFFICIENT_DATA);
        assertThat(res.fallbackUsed()).isTrue();
        assertThat(res.fallbackReason()).isEqualTo(AiFallbackReason.INSUFFICIENT_DATA_SHORT_CIRCUIT);
        assertThat(res.explanation().summary()).contains("INSUFFICIENT_DATA");
    }

    @Test
    void returnsExternalAiResultWhenProviderOutputPassesSchemaAndGuardrails() throws Exception {
        TechnicalScoreRun readyRun = readyScoreRun();
        when(scoreRunRepository.findById(scoreRunId)).thenReturn(Optional.of(readyRun));
        when(externalProvider.generate(any()))
                .thenReturn(
                        new AiRawGenerationResult(
                                """
                                {
                                  "summary": "THYAO scored 72.50/100 on 1d simulated bars, indicating positive trend balance.",
                                  "observedFacts": ["Latest close 298.50 TRY; RSI 58.40; score 72.50/100."],
                                  "interpretation": ["Moving averages reflect positive short-term alignment."],
                                  "uncertaintiesAndRisks": ["Simulated feed data; excludes fundamentals and news."],
                                  "confidenceNote": "Grounded in deterministic simulated indicators."
                                }
                                """,
                                "ollama",
                                "llama3.2:3b"));

        AiExplanationResponse res =
                service.getOrGenerateExplanation("THYAO", "1d", false, scoreRunId, false);

        assertThat(res.status()).isEqualTo(AiExplanationStatus.GENERATED);
        assertThat(res.fallbackUsed()).isFalse();
        assertThat(res.fallbackReason()).isNull();
        assertThat(res.providerId()).isEqualTo("ollama");
        assertThat(res.modelName()).isEqualTo("llama3.2:3b");
        assertThat(res.promptVersion()).isEqualTo(AiPromptBuilder.PROMPT_VERSION);
    }

    private TechnicalScoreRun readyScoreRun() throws Exception {
        String componentsJson =
                objectMapper.writeValueAsString(
                        List.of(
                                new ScoreComponentResult(
                                        ScoreComponentType.TREND,
                                        IndicatorStatus.READY,
                                        new BigDecimal("20"),
                                        new BigDecimal("60.00"),
                                        new BigDecimal("12.00"),
                                        "Close above SMA(20).",
                                        Map.of("close", new BigDecimal("298.50")))));
        String explanationJson =
                objectMapper.writeValueAsString(
                        new ScoreExplanation(
                                "Constructive bullish alignment.",
                                List.of("Positive moving average slope."),
                                List.of("Simulated dataset."),
                                "Weighted score engine",
                                "Decision support only."));

        return new TechnicalScoreRun(
                scoreRunId,
                UUID.randomUUID(),
                thyao.id(),
                "THYAO",
                "BIST",
                BarInterval.ONE_DAY,
                "mock",
                false,
                true,
                "score-engine-v1.0.0",
                ScoreStatus.READY,
                false,
                new BigDecimal("72.50"),
                ScoreRegime.MODERATE_BULLISH_TECHNICAL,
                TrendDirection.BULLISH,
                new BigDecimal("298.5000"),
                new BigDecimal("58.400000"),
                "{}",
                componentsJson,
                explanationJson,
                FIXED_NOW.minusSeconds(86400 * 90L),
                FIXED_NOW,
                FIXED_NOW,
                FIXED_NOW);
    }
}
