package com.stockvision.ai.domain;

/**
 * Abstraction over optional free AI explanation providers (deterministic template, local Ollama,
 * or user-supplied free-tier Gemini).
 */
public interface AiExplanationProvider {

    String providerId();

    String modelName();

    boolean isConfigured();

    AiRawGenerationResult generate(AiPromptBundle promptBundle);
}
