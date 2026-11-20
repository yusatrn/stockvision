package com.stockvision.ai.domain;

/**
 * Explicit reason explaining why deterministic template output or fallback occurred.
 */
public enum AiFallbackReason {
    TEMPLATE_MODE_SELECTED,
    PROVIDER_UNCONFIGURED,
    PROVIDER_UNAVAILABLE,
    PROVIDER_TIMEOUT,
    MALFORMED_OUTPUT,
    SCHEMA_VALIDATION_FAILED,
    GUARDRAIL_VIOLATION,
    INSUFFICIENT_DATA_SHORT_CIRCUIT
}
