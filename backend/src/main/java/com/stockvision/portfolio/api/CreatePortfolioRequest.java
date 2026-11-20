package com.stockvision.portfolio.api;

import com.stockvision.portfolio.domain.FeeTaxPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload to create a virtual portfolio container.
 */
public record CreatePortfolioRequest(
        @NotBlank(message = "name is required")
                @Size(max = 80, message = "name must not exceed 80 characters")
                String name,
        @Size(max = 255, message = "description must not exceed 255 characters")
                String description,
        @Size(max = 8, message = "baseCurrency must not exceed 8 characters")
                String baseCurrency,
        FeeTaxPolicy feeTaxPolicy) {
}
