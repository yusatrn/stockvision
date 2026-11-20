package com.stockvision.ai.domain;

/**
 * Lifecycle outcome of an explanation request for a persisted analysis run.
 */
public enum AiExplanationStatus {
    GENERATED,
    FALLBACK,
    INSUFFICIENT_DATA
}
