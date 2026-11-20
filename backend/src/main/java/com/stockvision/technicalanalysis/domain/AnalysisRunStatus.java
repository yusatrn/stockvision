package com.stockvision.technicalanalysis.domain;

/**
 * Overall sufficiency status across all requested indicators in an analysis run.
 */
public enum AnalysisRunStatus {
    READY,
    PARTIAL,
    INSUFFICIENT_DATA
}
