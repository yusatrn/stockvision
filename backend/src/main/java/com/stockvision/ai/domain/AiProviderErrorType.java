package com.stockvision.ai.domain;

/**
 * Categorized failure mode surfaced by an external free {@link AiExplanationProvider}.
 */
public enum AiProviderErrorType {
    UNCONFIGURED,
    TIMEOUT,
    UNAVAILABLE
}
