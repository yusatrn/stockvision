package com.stockvision.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stockvision.marketdata.domain.DataProviderMode;
import com.stockvision.marketdata.domain.FreshnessStatus;
import com.stockvision.scoring.domain.ScoreRegime;
import com.stockvision.scoring.domain.ScoreStatus;
import com.stockvision.scoring.domain.TrendDirection;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiPromptBuilderTest {

    @Test
    void buildsFactOnlyVersionedPromptProhibitingHallucinationsAndPersonalData() {
        ObjectMapper mapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        AiPromptBuilder builder = new AiPromptBuilder(mapper);

        AiFactPack facts =
                new AiFactPack(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "THYAO",
                        "Turkish Airlines",
                        "BIST",
                        "Transportation",
                        "TRY",
                        "1d",
                        false,
                        "score-engine-v1.0.0",
                        ScoreStatus.READY,
                        false,
                        new BigDecimal("72.50"),
                        ScoreRegime.MODERATE_BULLISH_TECHNICAL,
                        TrendDirection.BULLISH,
                        new BigDecimal("298.5000"),
                        new BigDecimal("58.400000"),
                        Instant.parse("2026-11-20T08:00:00Z"),
                        Instant.parse("2026-11-20T08:05:00Z"),
                        "Mock Provider",
                        DataProviderMode.SIMULATED,
                        true,
                        0,
                        FreshnessStatus.SIMULATED_ONLY,
                        "Simulated feed",
                        List.of(
                                new AiFactPack.FactComponent(
                                        "TREND",
                                        "READY",
                                        new BigDecimal("20"),
                                        new BigDecimal("60.00"),
                                        new BigDecimal("12.00"),
                                        "Price above SMA(20).",
                                        Map.of("close", new BigDecimal("298.50")))),
                        "Constructive bullish alignment.",
                        List.of("Trend component positive."),
                        List.of("Simulated dataset."));

        AiPromptBundle bundle = builder.build(facts);
        assertThat(bundle.promptVersion()).isEqualTo(AiPromptBuilder.PROMPT_VERSION);
        assertThat(bundle.systemPrompt())
                .contains("NEVER invent prices")
                .contains("observedFacts")
                .contains("interpretation")
                .contains("uncertaintiesAndRisks")
                .contains("profit guarantees");
        assertThat(bundle.userPromptJson())
                .contains("\"symbol\":\"THYAO\"")
                .doesNotContain("portfolio")
                .doesNotContain("email")
                .doesNotContain("userId");
    }
}
