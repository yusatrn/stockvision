package com.stockvision.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockvision.TestSecuritySupport;
import com.stockvision.auth.api.LoginRequest;
import com.stockvision.auth.api.LoginResponse;
import com.stockvision.auth.api.LogoutRequest;
import com.stockvision.auth.api.RefreshTokenRequest;
import com.stockvision.auth.api.RegisterRequest;
import com.stockvision.auth.api.RegisterResponse;
import com.stockvision.auth.infrastructure.RefreshTokenHasher;
import com.stockvision.common.api.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.security.rate-limit.max-requests=50")
@ActiveProfiles("test")
@Testcontainers
class AuthRefreshLogoutIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        TestSecuritySupport.registerJwtSecret(registry);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rotatesRefreshTokenDetectsReplayReuseAndRevokesOnLogout() {
        String email = "refresh.hardening@example.com";
        restTemplate.postForEntity(
                "/api/v1/auth/register",
                new RegisterRequest(email, "HardeningPass123", "Hardening User", "Europe/Istanbul"),
                RegisterResponse.class);

        LoginResponse login =
                restTemplate
                        .postForEntity(
                                "/api/v1/auth/login",
                                new LoginRequest(email, "HardeningPass123"),
                                LoginResponse.class)
                        .getBody();
        assertThat(login).isNotNull();
        assertThat(login.accessToken()).isNotBlank();
        assertThat(login.refreshToken()).isNotBlank();
        assertThat(login.refreshExpiresAt()).isNotNull();

        // Only SHA-256 hash stored in DB, never plaintext token
        String expectedHash = RefreshTokenHasher.sha256Hex(login.refreshToken());
        Integer storedHashedRows =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = ?",
                        Integer.class,
                        expectedHash);
        assertThat(storedHashedRows).isEqualTo(1);

        // Rotate refresh token
        ResponseEntity<LoginResponse> refreshed =
                restTemplate.postForEntity(
                        "/api/v1/auth/refresh",
                        new RefreshTokenRequest(login.refreshToken()),
                        LoginResponse.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponse rotated = refreshed.getBody();
        assertThat(rotated).isNotNull();
        assertThat(rotated.refreshToken()).isNotBlank().isNotEqualTo(login.refreshToken());

        // New access token works on protected endpoint
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(rotated.accessToken());
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/users/me",
                                        HttpMethod.GET,
                                        new HttpEntity<>(authHeaders),
                                        String.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // Replay old already-rotated refresh token -> 401 AND revokes rotated token family
        ResponseEntity<ApiErrorResponse> replayAttempt =
                restTemplate.postForEntity(
                        "/api/v1/auth/refresh",
                        new RefreshTokenRequest(login.refreshToken()),
                        ApiErrorResponse.class);
        assertThat(replayAttempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Rotated token is now ALSO rejected because replay detection revoked active user tokens
        ResponseEntity<ApiErrorResponse> familyRevokedCheck =
                restTemplate.postForEntity(
                        "/api/v1/auth/refresh",
                        new RefreshTokenRequest(rotated.refreshToken()),
                        ApiErrorResponse.class);
        assertThat(familyRevokedCheck.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Fresh login + explicit logout revokes token idempotently
        LoginResponse secondLogin =
                restTemplate
                        .postForEntity(
                                "/api/v1/auth/login",
                                new LoginRequest(email, "HardeningPass123"),
                                LoginResponse.class)
                        .getBody();
        assertThat(secondLogin).isNotNull();

        ResponseEntity<Void> logoutRes =
                restTemplate.postForEntity(
                        "/api/v1/auth/logout",
                        new LogoutRequest(secondLogin.refreshToken()),
                        Void.class);
        assertThat(logoutRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ApiErrorResponse> afterLogoutRefresh =
                restTemplate.postForEntity(
                        "/api/v1/auth/refresh",
                        new RefreshTokenRequest(secondLogin.refreshToken()),
                        ApiErrorResponse.class);
        assertThat(afterLogoutRefresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
