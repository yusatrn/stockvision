package com.stockvision.ai.domain;

import java.util.List;
import java.util.Objects;

/**
 * Schema-validated natural-language explanation distinguishing facts, interpretation, and risks.
 */
public record StructuredAiExplanation(
        String summary,
        List<String> observedFacts,
        List<String> interpretation,
        List<String> uncertaintiesAndRisks,
        String confidenceNote) {

    public StructuredAiExplanation {
        Objects.requireNonNull(summary, "summary must not be null");
        observedFacts = List.copyOf(Objects.requireNonNull(observedFacts, "observedFacts required"));
        interpretation = List.copyOf(Objects.requireNonNull(interpretation, "interpretation required"));
        uncertaintiesAndRisks =
                List.copyOf(
                        Objects.requireNonNull(
                                uncertaintiesAndRisks, "uncertaintiesAndRisks required"));
        Objects.requireNonNull(confidenceNote, "confidenceNote must not be null");
    }
}
