package com.stockvision.technicalanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockvision.TestSecuritySupport;
import com.stockvision.auth.api.LoginRequest;
import com.stockvision.auth.api.LoginResponse;
import com.stockvision.auth.api.RegisterRequest;
import com.stockvision.auth.api.RegisterResponse;
import com.stockvision.common.api.ApiErrorResponse;
import com.stockvision.marketdata.domain.FreshnessStatus;
import com.stockvision.technicalanalysis.api.IndicatorSeriesResponse;
import com.stockvision.technicalanalysis.api.TechnicalIndicatorsResponse;
import com.stockvision.technicalanalysis.domain.AnalysisRunStatus;
import com.stockvision.technicalanalysis.domain.DeterministicTechnicalIndicatorEngine;
import com.stockvision.technicalanalysis.domain.IndicatorStatus;
import com.stockvision.technicalanalysis.domain.IndicatorType;
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
class TechnicalAnalysisIntegrationTest {

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
    void calculatesAllIndicatorsAndPersistsVersionedAnalysisRun() {
        HttpHeaders headers = registerAndLogin("ta.full@example.com");

        ResponseEntity<TechnicalIndicatorsResponse> response =
                restTemplate.exchange(
                        "/api/v1/instruments/THYAO/indicators?interval=1d"
                                + "&indicators=RSI,MACD,EMA,BOLLINGER,STOCHASTIC,ATR,SMA,VOLUME_AVG"
                                + "&from=2026-06-01T00:00:00Z&to=2026-11-18T18:00:00Z",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        TechnicalIndicatorsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TechnicalIndicatorsResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.analysisRunId()).isNotNull();
        assertThat(body.symbol()).isEqualTo("THYAO");
        assertThat(body.engineVersion())
                .isEqualTo(DeterministicTechnicalIndicatorEngine.ENGINE_VERSION);
        assertThat(body.status()).isEqualTo(AnalysisRunStatus.READY);
        assertThat(body.dataSource().simulated()).isTrue();
        assertThat(body.freshness().status()).isEqualTo(FreshnessStatus.SIMULATED_ONLY);
        assertThat(body.disclaimer()).contains("gelecekteki getirileri öngörmez");
        assertThat(body.indicators()).hasSize(8);
        assertThat(body.indicators())
                .allMatch(
                        s ->
                                s.status() == IndicatorStatus.READY
                                        && !s.latestValues().isEmpty()
                                        && !s.parameters().isEmpty());

        String savedEngineVersion =
                jdbcTemplate.queryForObject(
                        "SELECT engine_version FROM analysis_runs WHERE id = ?",
                        String.class,
                        body.analysisRunId());
        assertThat(savedEngineVersion)
                .isEqualTo(DeterministicTechnicalIndicatorEngine.ENGINE_VERSION);

        String savedParams =
                jdbcTemplate.queryForObject(
                        "SELECT parameters_json FROM analysis_runs WHERE id = ?",
                        String.class,
                        body.analysisRunId());
        assertThat(savedParams).contains("\"RSI\"").contains("\"WILDER_RMA\"").contains("\"MACD\"");
    }

    @Test
    void handlesInsufficientAndPartialDataExplicitlyWithoutFabricatingValues() {
        HttpHeaders headers = registerAndLogin("ta.partial@example.com");

        ResponseEntity<TechnicalIndicatorsResponse> insufficientResponse =
                restTemplate.exchange(
                        "/api/v1/instruments/GARAN/indicators?interval=1d"
                                + "&indicators=MACD,RSI&from=2026-11-09T00:00:00Z&to=2026-11-13T18:00:00Z",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        TechnicalIndicatorsResponse.class);

        assertThat(insufficientResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        TechnicalIndicatorsResponse insufficient = insufficientResponse.getBody();
        assertThat(insufficient).isNotNull();
        assertThat(insufficient.status()).isEqualTo(AnalysisRunStatus.INSUFFICIENT_DATA);
        assertThat(insufficient.indicators())
                .allMatch(
                        s ->
                                s.status() == IndicatorStatus.INSUFFICIENT_DATA
                                        && s.latestValues().isEmpty());

        String persistedStatus =
                jdbcTemplate.queryForObject(
                        "SELECT status FROM analysis_runs WHERE id = ?",
                        String.class,
                        insufficient.analysisRunId());
        assertThat(persistedStatus).isEqualTo("INSUFFICIENT_DATA");

        ResponseEntity<TechnicalIndicatorsResponse> partialResponse =
                restTemplate.exchange(
                        "/api/v1/instruments/AKBNK/indicators?interval=1d"
                                + "&indicators=SMA,MACD&smaPeriod=5"
                                + "&from=2026-10-15T00:00:00Z&to=2026-11-13T18:00:00Z",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        TechnicalIndicatorsResponse.class);

        assertThat(partialResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        TechnicalIndicatorsResponse partial = partialResponse.getBody();
        assertThat(partial).isNotNull();
        assertThat(partial.status()).isEqualTo(AnalysisRunStatus.PARTIAL);

        IndicatorSeriesResponse smaSeries =
                partial.indicators().stream()
                        .filter(s -> s.type() == IndicatorType.SMA)
                        .findFirst()
                        .orElseThrow();
        IndicatorSeriesResponse macdSeries =
                partial.indicators().stream()
                        .filter(s -> s.type() == IndicatorType.MACD)
                        .findFirst()
                        .orElseThrow();
        assertThat(smaSeries.status()).isEqualTo(IndicatorStatus.READY);
        assertThat(macdSeries.status()).isEqualTo(IndicatorStatus.INSUFFICIENT_DATA);
    }

    @Test
    void validatesInputsSecurityAndOpenApiDocumentation() {
        assertThat(
                        restTemplate
                                .getForEntity(
                                        "/api/v1/instruments/THYAO/indicators",
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders headers = registerAndLogin("ta.validation@example.com");

        ResponseEntity<ApiErrorResponse> unknownIndicator =
                restTemplate.exchange(
                        "/api/v1/instruments/THYAO/indicators?indicators=FOO",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        ApiErrorResponse.class);
        assertThat(unknownIndicator.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unknownIndicator.getBody().code()).isEqualTo("VALIDATION_ERROR");

        ResponseEntity<ApiErrorResponse> invalidMacd =
                restTemplate.exchange(
                        "/api/v1/instruments/THYAO/indicators?macdFast=30&macdSlow=10",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        ApiErrorResponse.class);
        assertThat(invalidMacd.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidMacd.getBody().code()).isEqualTo("VALIDATION_ERROR");

        ResponseEntity<ApiErrorResponse> missingSymbol =
                restTemplate.exchange(
                        "/api/v1/instruments/UNKNOWN999/indicators",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        ApiErrorResponse.class);
        assertThat(missingSymbol.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> openApi = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(openApi.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(openApi.getBody()).contains("/api/v1/instruments/{symbol}/indicators");
    }

    private HttpHeaders registerAndLogin(String email) {
        restTemplate.postForEntity(
                "/api/v1/auth/register",
                new RegisterRequest(email, "TaTestPass123", "TA Tester", "Europe/Istanbul"),
                RegisterResponse.class);
        LoginResponse login =
                restTemplate
                        .postForEntity(
                                "/api/v1/auth/login",
                                new LoginRequest(email, "TaTestPass123"),
                                LoginResponse.class)
                        .getBody();
        assertThat(login).isNotNull();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.accessToken());
        return headers;
    }
}
