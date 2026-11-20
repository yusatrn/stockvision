package com.stockvision.alert.api;

import com.stockvision.alert.domain.AlertStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Partial update payload for toggling alert status or tuning threshold/cooldown settings.
 */
public record UpdateAlertRequest(
        AlertStatus status,
        BigDecimal thresholdValue,
        @Min(value = 1, message = "cooldownMinutes must be at least 1")
                @Max(value = 43200, message = "cooldownMinutes must not exceed 43200")
                Integer cooldownMinutes,
        @Size(max = 255, message = "note must not exceed 255 characters") String note) {
}
