package com.stockvision.watchlist.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload to add an active instrument to a watchlist.
 */
public record AddWatchlistItemRequest(
        @NotBlank(message = "symbol is required")
                @Size(max = 24, message = "symbol must not exceed 24 characters")
                String symbol,
        @Size(max = 255, message = "notes must not exceed 255 characters") String notes) {
}
