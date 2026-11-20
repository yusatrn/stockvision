package com.stockvision.ai.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockvision.ai.domain.AiExplanationProvider;
import com.stockvision.ai.domain.AiFactPack;
import com.stockvision.ai.domain.AiPromptBundle;
import com.stockvision.ai.domain.AiRawGenerationResult;
import com.stockvision.ai.domain.DeterministicTemplateExplanationGenerator;
import com.stockvision.ai.domain.StructuredAiExplanation;

/**
 * Built-in zero-cost deterministic template provider used as default mode and universal fallback.
 */
public class DeterministicTemplateAiProvider implements AiExplanationProvider {

    public static final String PROVIDER_ID = "deterministic-template";
    public static final String MODEL_NAME = "template-v1.0.0";

    private final DeterministicTemplateExplanationGenerator generator;
    private final ObjectMapper objectMapper;

    public DeterministicTemplateAiProvider(
            DeterministicTemplateExplanationGenerator generator, ObjectMapper objectMapper) {
        this.generator = generator;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public String modelName() {
        return MODEL_NAME;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public AiRawGenerationResult generate(AiPromptBundle promptBundle) {
        try {
            AiFactPack facts =
                    objectMapper.readValue(promptBundle.userPromptJson(), AiFactPack.class);
            StructuredAiExplanation explanation = generator.generate(facts);
            return new AiRawGenerationResult(
                    objectMapper.writeValueAsString(explanation), PROVIDER_ID, MODEL_NAME);
        } catch (Exception ex) {
            throw new IllegalStateException("Deterministic template generation failed", ex);
        }
    }
}
