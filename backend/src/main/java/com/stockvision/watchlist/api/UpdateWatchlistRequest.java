package com.stockvision.watchlist.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload to rename or update description of a watchlist.
 */
public record UpdateWatchlistRequest(
        @NotBlank(message = "name is required")
                @Size(max = 80, message = "name must not exceed 80 characters")
                String name,
        @Size(max = 255, message = "description must not exceed 255 characters")
                String description) {
}
