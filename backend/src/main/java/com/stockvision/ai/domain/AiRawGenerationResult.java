package com.stockvision.ai.domain;

import java.util.Objects;

/**
 * Unvalidated raw text payload returned by an {@link AiExplanationProvider}.
 */
public record AiRawGenerationResult(
        String rawText,
        String providerId,
        String modelName) {

    public AiRawGenerationResult {
        Objects.requireNonNull(rawText, "rawText must not be null");
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
    }
}
