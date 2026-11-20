package com.stockvision.auth.api;

import jakarta.validation.constraints.Size;

/**
 * Optional logout payload carrying the client's active opaque refresh token for revocation.
 */
public record LogoutRequest(
        @Size(max = 512, message = "refreshToken length is invalid") String refreshToken) {
}
