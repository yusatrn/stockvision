package com.stockvision.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.stockvision.TestSecuritySupport;
import com.stockvision.auth.api.LoginRequest;
import com.stockvision.auth.api.LoginResponse;
import com.stockvision.auth.api.RegisterRequest;
import com.stockvision.auth.api.RegisterResponse;
import com.stockvision.common.api.ApiErrorResponse;
import com.stockvision.marketdata.domain.DataProviderMode;
import com.stockvision.marketdata.domain.MarketDataProvider;
import com.stockvision.marketdata.domain.MarketDataProviderException;
import com.stockvision.marketdata.domain.ProviderMetadata;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.security.rate-limit.max-requests=50")
@ActiveProfiles("test")
@Testcontainers
class MarketDataProviderFailureIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        TestSecuritySupport.registerJwtSecret(registry);
    }

    @MockitoBean
    private MarketDataProvider marketDataProvider;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void providerFailureReturnsServiceUnavailableAndCommitsFailedSyncAudit() {
        when(marketDataProvider.getProviderMetadata())
                .thenReturn(
                        new ProviderMetadata(
                                "MOCK_SIMULATED",
                                "Mock Provider",
                                DataProviderMode.SIMULATED,
                                true,
                                0,
                                "Simulated",
                                "Europe/Istanbul"));
        when(marketDataProvider.getHistoricalBars(
                        anyString(), any(), any(), any(), anyBoolean()))
                .thenThrow(new MarketDataProviderException("Simulated upstream failure"));

        restTemplate.postForEntity(
                "/api/v1/auth/register",
                new RegisterRequest(
                        "provider.fail@example.com",
                        "FailTestPass123",
                        "Fail Tester",
                        "Europe/Istanbul"),
                RegisterResponse.class);
        LoginResponse login =
                restTemplate
                        .postForEntity(
                                "/api/v1/auth/login",
                                new LoginRequest("provider.fail@example.com", "FailTestPass123"),
                                LoginResponse.class)
                        .getBody();
        assertThat(login).isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.accessToken());
        ResponseEntity<ApiErrorResponse> response =
                restTemplate.exchange(
                        "/api/v1/instruments/THYAO/bars?interval=1d",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("PROVIDER_UNAVAILABLE");

        Integer failedCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM provider_sync_runs WHERE symbol = 'THYAO' AND status = 'FAILED'",
                        Integer.class);
        assertThat(failedCount).isEqualTo(1);
    }
}
