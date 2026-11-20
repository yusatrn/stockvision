package com.stockvision.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockvision.TestSecuritySupport;
import com.stockvision.auth.api.LoginRequest;
import com.stockvision.auth.api.RegisterRequest;
import com.stockvision.common.api.ApiErrorResponse;
import com.stockvision.common.web.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.security.rate-limit.enabled=true",
            "app.security.rate-limit.max-requests=3",
            "app.security.rate-limit.window-seconds=60"
        })
@ActiveProfiles("test")
@Testcontainers
class AuthRateLimitIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        TestSecuritySupport.registerJwtSecret(registry);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void throttlesExcessiveLoginAndRegisterAttemptsPerEndpointBucket() {
        LoginRequest loginRequest = new LoginRequest("ratelimit@example.com", "WrongPass123");
        for (int i = 0; i < 3; i++) {
            ResponseEntity<ApiErrorResponse> attempt =
                    restTemplate.postForEntity(
                            "/api/v1/auth/login", loginRequest, ApiErrorResponse.class);
            assertThat(attempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<ApiErrorResponse> blockedLogin =
                restTemplate.postForEntity(
                        "/api/v1/auth/login", loginRequest, ApiErrorResponse.class);
        assertThat(blockedLogin.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blockedLogin.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(
                        Long.parseLong(
                                blockedLogin.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)))
                .isGreaterThanOrEqualTo(1L);
        assertThat(blockedLogin.getHeaders().getFirst(TraceIdFilter.HEADER_NAME)).isNotBlank();
        assertThat(blockedLogin.getBody()).isNotNull();
        assertThat(blockedLogin.getBody().code()).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(blockedLogin.getBody().path()).isEqualTo("/api/v1/auth/login");

        RegisterRequest registerRequest =
                new RegisterRequest(
                        "ratelimit@example.com", "ValidPass123", "Rate User", "Europe/Istanbul");
        for (int i = 0; i < 3; i++) {
            ResponseEntity<String> attempt =
                    restTemplate.postForEntity("/api/v1/auth/register", registerRequest, String.class);
            assertThat(attempt.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        }

        ResponseEntity<ApiErrorResponse> blockedRegister =
                restTemplate.postForEntity(
                        "/api/v1/auth/register", registerRequest, ApiErrorResponse.class);
        assertThat(blockedRegister.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blockedRegister.getBody()).isNotNull();
        assertThat(blockedRegister.getBody().code()).isEqualTo("TOO_MANY_REQUESTS");
    }
}
