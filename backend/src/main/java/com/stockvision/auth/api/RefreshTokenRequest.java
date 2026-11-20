package com.stockvision.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body to exchange an opaque refresh token for a rotated access/refresh token pair.
 */
public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken is required")
                @Size(max = 512, message = "refreshToken length is invalid")
                String refreshToken) {
}
