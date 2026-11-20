package com.stockvision;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockvision.common.api.ApiErrorResponse;
import com.stockvision.common.api.HealthResponse;
import com.stockvision.common.web.TraceIdFilter;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class FoundationIntegrationTest {

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
    void flywayBaselineUsersMarketDataAnalysisAndScoringMigrationsApplied() {
        Integer appliedCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('1', '2', '3', '4', '5') AND success = TRUE",
                        Integer.class);
        assertThat(appliedCount).isEqualTo(5);

        String foundationVersion =
                jdbcTemplate.queryForObject(
                        "SELECT meta_value FROM system_schema_metadata WHERE meta_key = 'foundation_version'",
                        String.class);
        assertThat(foundationVersion).isEqualTo("1");
    }

    @Test
    void healthActuatorAndOpenApiEndpointsRespondSuccessfully() {
        ResponseEntity<HealthResponse> healthResponse =
                restTemplate.getForEntity("/api/v1/health", HealthResponse.class);

        assertThat(healthResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(healthResponse.getHeaders().getFirst(TraceIdFilter.HEADER_NAME)).isNotBlank();
        assertThat(healthResponse.getBody()).isNotNull();
        assertThat(healthResponse.getBody().status()).isEqualTo("UP");
        assertThat(healthResponse.getBody().service()).isEqualTo("stockvision-backend");
        assertThat(healthResponse.getBody().dataProviderMode()).isEqualTo("SIMULATED");
        assertThat(healthResponse.getBody().timestamp()).isNotNull();
        assertThat(healthResponse.getBody().disclaimer()).contains("yatırım danışmanlığı");

        ResponseEntity<String> actuatorResponse =
                restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(actuatorResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(actuatorResponse.getBody()).contains("\"status\":\"UP\"");

        ResponseEntity<String> openApiResponse =
                restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(openApiResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(openApiResponse.getBody())
                .contains("StockVision AI API")
                .contains("/api/v1/health");
    }

    @Test
    void unauthenticatedProtectedEndpointReturnsConsistentJsonError() {
        ResponseEntity<ApiErrorResponse> response =
                restTemplate.getForEntity("/api/v1/protected-probe", ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String headerTraceId = response.getHeaders().getFirst(TraceIdFilter.HEADER_NAME);
        assertThat(headerTraceId).isNotBlank();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(401);
        assertThat(response.getBody().code()).isEqualTo("UNAUTHORIZED");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/protected-probe");
        assertThat(response.getBody().traceId()).isEqualTo(headerTraceId);
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void corsAllowsConfiguredOriginAndRejectsUntrustedOrigin() {
        RequestEntity<Void> allowedPreflight =
                RequestEntity.options(URI.create("/api/v1/health"))
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .build();

        ResponseEntity<String> allowedResponse =
                restTemplate.exchange(allowedPreflight, String.class);
        assertThat(allowedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(allowedResponse.getHeaders().getAccessControlAllowOrigin())
                .isEqualTo("http://localhost:3000");

        RequestEntity<Void> rejectedPreflight =
                RequestEntity.options(URI.create("/api/v1/health"))
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .build();

        ResponseEntity<String> rejectedResponse =
                restTemplate.exchange(rejectedPreflight, String.class);
        assertThat(rejectedResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rejectedResponse.getHeaders().getAccessControlAllowOrigin()).isNull();
    }
}
