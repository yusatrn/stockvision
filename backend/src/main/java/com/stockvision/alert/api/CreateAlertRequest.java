package com.stockvision.alert.api;

import com.stockvision.alert.domain.AlertConditionType;
import com.stockvision.technicalanalysis.domain.IndicatorType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Payload to create a user price or indicator threshold alert rule.
 */
public record CreateAlertRequest(
        @NotBlank(message = "symbol is required")
                @Size(max = 24, message = "symbol must not exceed 24 characters")
                String symbol,
        @NotNull(message = "conditionType is required") AlertConditionType conditionType,
        IndicatorType indicatorType,
        @Size(max = 32, message = "indicatorField must not exceed 32 characters")
                String indicatorField,
        @Size(max = 16, message = "interval must not exceed 16 characters") String interval,
        @NotNull(message = "thresholdValue is required") BigDecimal thresholdValue,
        @Min(value = 1, message = "cooldownMinutes must be at least 1")
                @Max(value = 43200, message = "cooldownMinutes must not exceed 43200")
                Integer cooldownMinutes,
        @Size(max = 255, message = "note must not exceed 255 characters") String note) {
}
