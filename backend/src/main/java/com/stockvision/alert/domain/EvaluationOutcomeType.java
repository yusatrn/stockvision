package com.stockvision.alert.domain;

/**
 * Machine-readable outcome emitted for a single alert rule evaluation pass.
 */
public enum EvaluationOutcomeType {
    TRIGGERED,
    NOT_MET,
    COOLDOWN_SUPPRESSED,
    DUPLICATE_SUPPRESSED,
    SKIPPED_STALE_DATA,
    SKIPPED_INSUFFICIENT_DATA
}
