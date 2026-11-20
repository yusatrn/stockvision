package com.stockvision;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockvision.auth.api.LoginRequest;
import com.stockvision.auth.api.LoginResponse;
import com.stockvision.auth.api.RegisterRequest;
import com.stockvision.auth.api.RegisterResponse;
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
class ReleaseHardeningIntegrationTest {

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
    void verifiesFlywayMigrationsActuatorProbesSecurityHeadersPaginationAndOpenApi() {
        // 1. All 9 Flyway migrations applied cleanly from an empty PostgreSQL 16 database
        Integer successfulMigrations =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                        Integer.class);
        assertThat(successfulMigrations).isEqualTo(9);

        // 2. Actuator health & liveness/readiness probes UP; sensitive actuator endpoints blocked
        ResponseEntity<String> health = restTemplate.getForEntity("/actuator/health", String.class);
        ResponseEntity<String> liveness =
                restTemplate.getForEntity("/actuator/health/liveness", String.class);
        ResponseEntity<String> readiness =
                restTemplate.getForEntity("/actuator/health/readiness", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("UP");
        assertThat(liveness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(liveness.getBody()).contains("UP");
        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readiness.getBody()).contains("UP");

        assertThat(restTemplate.getForEntity("/actuator/env", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.getForEntity("/actuator/beans", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // 3. HTTP security headers & X-Trace-Id present on API responses
        ResponseEntity<String> apiHealth = restTemplate.getForEntity("/api/v1/health", String.class);
        assertThat(apiHealth.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(apiHealth.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(apiHealth.getHeaders().getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(apiHealth.getHeaders().getFirst("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(apiHealth.getHeaders().getFirst("Permissions-Policy"))
                .contains("camera=()");
        assertThat(apiHealth.getHeaders().getFirst("X-Trace-Id")).isNotBlank();

        // 4. Register & login to verify pagination bounds and OpenAPI completeness
        String email = "release.check@example.com";
        restTemplate.postForEntity(
                "/api/v1/auth/register",
                new RegisterRequest(email, "ReleasePass123", "Release Checker", "Europe/Istanbul"),
                RegisterResponse.class);
        LoginResponse login =
                restTemplate
                        .postForEntity(
                                "/api/v1/auth/login",
                                new LoginRequest(email, "ReleasePass123"),
                                LoginResponse.class)
                        .getBody();
        assertThat(login).isNotNull();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.accessToken());

        // Oversized page size (>100) rejected deterministically
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/instruments?page=0&size=101",
                                        HttpMethod.GET,
                                        new HttpEntity<>(headers),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/notifications?page=0&size=101",
                                        HttpMethod.GET,
                                        new HttpEntity<>(headers),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // 5. OpenAPI documents new Phase 9 auth endpoints
        ResponseEntity<String> openApi = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(openApi.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(openApi.getBody())
                .contains("/api/v1/auth/refresh")
                .contains("/api/v1/auth/logout");
    }
}
