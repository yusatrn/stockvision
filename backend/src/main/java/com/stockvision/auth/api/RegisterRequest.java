package com.stockvision.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating a new user account.
 */
public record RegisterRequest(
        @NotBlank(message = "Email is required")
                @Email(message = "Email must be a valid email address")
                @Size(max = 254, message = "Email must not exceed 254 characters")
                String email,
        @NotBlank(message = "Password is required")
                @Size(min = 10, max = 128, message = "Password must be between 10 and 128 characters")
                @Pattern(
                        regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                        message = "Password must contain at least one letter and one number")
                String password,
        @Size(max = 100, message = "Display name must not exceed 100 characters")
                String displayName,
        @Size(max = 64, message = "Timezone must not exceed 64 characters")
                String timezone) {

    public RegisterRequest {
        if (email != null) {
            email = email.trim();
        }
    }
}
