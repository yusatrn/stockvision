package com.stockvision.auth.api;

import com.stockvision.auth.application.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints for user registration, JWT login, refresh token rotation, and
 * session logout.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(
        name = "Authentication",
        description = "User registration, JWT authentication, and rotatable refresh token lifecycle")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(
            summary = "Register user account",
            description =
                    "Creates a user account when eligible and returns a neutral response to prevent email enumeration.")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.accepted().body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Authenticate with email and password",
            description =
                    "Validates credentials and returns a Bearer JWT access token plus an opaque rotatable refresh token.")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Rotate refresh token",
            description =
                    "Exchanges an active opaque refresh token for a fresh JWT access token and rotated refresh token; detects replay reuse and revokes active tokens.")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Logout and revoke refresh token",
            description =
                    "Idempotently revokes the supplied refresh token if active so subsequent refreshes are rejected.")
    public ResponseEntity<Void> logout(@Valid @RequestBody(required = false) LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}
