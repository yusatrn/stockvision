package com.stockvision.ai.domain;

import java.util.Objects;

/**
 * Versioned prompt payload passed to an {@link AiExplanationProvider}.
 */
public record AiPromptBundle(
        String promptVersion,
        String systemPrompt,
        String userPromptJson) {

    public AiPromptBundle {
        Objects.requireNonNull(promptVersion, "promptVersion must not be null");
        Objects.requireNonNull(systemPrompt, "systemPrompt must not be null");
        Objects.requireNonNull(userPromptJson, "userPromptJson must not be null");
    }
}
