package com.stockvision.ai.domain;

/**
 * Outcome of strict JSON parsing, schema validation, and non-advice guardrail checks.
 */
public record AiOutputValidationResult(
        boolean valid,
        StructuredAiExplanation explanation,
        AiFallbackReason rejectionReason,
        String detail) {

    public static AiOutputValidationResult accepted(StructuredAiExplanation explanation) {
        return new AiOutputValidationResult(true, explanation, null, "OK");
    }

    public static AiOutputValidationResult rejected(AiFallbackReason reason, String detail) {
        return new AiOutputValidationResult(false, null, reason, detail);
    }
}
