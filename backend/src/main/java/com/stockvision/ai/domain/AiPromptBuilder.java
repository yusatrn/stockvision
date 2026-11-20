package com.stockvision.ai.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;

/**
 * Constructs versioned prompts that strictly ground an LLM in persisted quantitative facts and
 * enforce explicit separation between facts, interpretation, and uncertainty.
 */
public class AiPromptBuilder {

    public static final String PROMPT_VERSION = "ai-explain-v1.1.0-tr";

    private static final String SYSTEM_PROMPT =
            """
            You are an analytical market-data explanation assistant for StockVision AI (decision support only).
            STRICT RULES:
            1. Use ONLY the numeric values, dates, indicators, and metadata supplied in the JSON fact pack.
            2. NEVER invent prices, percentage moves, indicator readings, financial fundamentals (P/E, revenue, EPS), earnings, dividends, macro events, or news.
            3. NEVER give personalized investment advice, buy/sell commands, target prices, profit guarantees, or risk-free return claims.
            4. Clearly distinguish:
               - "observedFacts": verifiable readings directly present in the input JSON (include source/simulated and timestamp context).
               - "interpretation": neutral technical synthesis strictly derived from those observed facts.
               - "uncertaintiesAndRisks": data limitations (simulated data, staleness, omitted fundamentals/news, indicator lag).
            5. Return ONLY a valid JSON object matching this exact schema (no markdown fences, no extra prose):
            {
              "summary": "1-2 neutral sentences summarizing the state (15-800 chars)",
              "observedFacts": ["fact 1", "fact 2"],
              "interpretation": ["interpretation 1"],
              "uncertaintiesAndRisks": ["risk or limitation 1"],
              "confidenceNote": "concise note referencing data sufficiency and feed mode (10-400 chars)"
            }
            6. Write all JSON string values in clear, natural Turkish (Türkçe).
            """;

    private final ObjectMapper objectMapper;

    public AiPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public AiPromptBundle build(AiFactPack factPack) {
        Objects.requireNonNull(factPack, "factPack must not be null");
        try {
            String json = objectMapper.writeValueAsString(factPack);
            return new AiPromptBundle(PROMPT_VERSION, SYSTEM_PROMPT, json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize AiFactPack", ex);
        }
    }
}
