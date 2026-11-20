package com.stockvision.alert.api;

import com.stockvision.alert.domain.EvaluationOutcomeType;
import com.stockvision.marketdata.api.DataSourceMetadataResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Summary response for a manual or diagnostic alert evaluation run.
 */
public record AlertEvaluationRunResponse(
        int evaluatedCount,
        int triggeredCount,
        int suppressedCount,
        Instant evaluatedAt,
        DataSourceMetadataResponse dataSource,
        List<EvaluatedRuleResult> results) {

    public record EvaluatedRuleResult(
            UUID alertRuleId,
            String symbol,
            EvaluationOutcomeType outcome,
            BigDecimal observedValue,
            String reason) {
    }
}
