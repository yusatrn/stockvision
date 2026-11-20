package com.stockvision.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for authenticating with email and password.
 */
public record LoginRequest(
        @NotBlank(message = "Email is required")
                @Email(message = "Email must be a valid email address")
                @Size(max = 254, message = "Email must not exceed 254 characters")
                String email,
        @NotBlank(message = "Password is required")
                @Size(max = 128, message = "Password must not exceed 128 characters")
                String password) {

    public LoginRequest {
        if (email != null) {
            email = email.trim();
        }
    }
}
