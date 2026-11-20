package com.stockvision.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockvision.TestSecuritySupport;
import com.stockvision.auth.api.LoginRequest;
import com.stockvision.auth.api.LoginResponse;
import com.stockvision.auth.api.RegisterRequest;
import com.stockvision.auth.api.RegisterResponse;
import com.stockvision.common.api.ApiErrorResponse;
import com.stockvision.marketdata.domain.FreshnessStatus;
import com.stockvision.scoring.api.InstrumentAnalysisResponse;
import com.stockvision.scoring.api.ScreenerItemResponse;
import com.stockvision.scoring.api.ScreenerPageResponse;
import com.stockvision.scoring.domain.DeterministicTechnicalScoringEngine;
import com.stockvision.scoring.domain.ScoreRegime;
import com.stockvision.scoring.domain.ScoreStatus;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
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
class ScoringIntegrationTest {

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
    void computesExplainableScoreAndPersistsVersionedRuns() {
        HttpHeaders headers = registerAndLogin("scoring.analysis@example.com");

        ResponseEntity<InstrumentAnalysisResponse> response =
                restTemplate.exchange(
                        "/api/v1/instruments/THYAO/analysis?interval=1d"
                                + "&from=2026-06-01T00:00:00Z&to=2026-11-18T18:00:00Z",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        InstrumentAnalysisResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        InstrumentAnalysisResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.scoreRunId()).isNotNull();
        assertThat(body.analysisRunId()).isNotNull();
        assertThat(body.algorithmVersion())
                .isEqualTo(DeterministicTechnicalScoringEngine.ALGORITHM_VERSION);
        assertThat(body.status()).isEqualTo(ScoreStatus.READY);
        assertThat(body.finalScore())
                .isNotNull()
                .isBetween(BigDecimal.ZERO, new BigDecimal("100.00"));
        assertThat(body.components()).hasSize(7);
        BigDecimal weightSum =
                body.weights().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(weightSum).isEqualByComparingTo("100.00");
        assertThat(body.explanation().headline()).contains("THYAO");
        assertThat(body.explanation().keyDrivers()).isNotEmpty();
        assertThat(body.explanation().disclaimer()).contains("öngörmez");
        assertThat(body.dataSource().simulated()).isTrue();
        assertThat(body.freshness().status()).isEqualTo(FreshnessStatus.SIMULATED_ONLY);

        String analysisRunType =
                jdbcTemplate.queryForObject(
                        "SELECT run_type FROM analysis_runs WHERE id = ?",
                        String.class,
                        body.analysisRunId());
        assertThat(analysisRunType).isEqualTo("TECHNICAL_SCORE");

        BigDecimal persistedFinalScore =
                jdbcTemplate.queryForObject(
                        "SELECT final_score FROM technical_score_runs WHERE id = ?",
                        BigDecimal.class,
                        body.scoreRunId());
        assertThat(persistedFinalScore).isEqualByComparingTo(body.finalScore());
    }

    @Test
    void shortHistoryPersistsInsufficientDataWithNullScore() {
        HttpHeaders headers = registerAndLogin("scoring.insufficient@example.com");

        ResponseEntity<InstrumentAnalysisResponse> response =
                restTemplate.exchange(
                        "/api/v1/instruments/GARAN/analysis?interval=1d"
                                + "&from=2026-11-09T00:00:00Z&to=2026-11-13T18:00:00Z",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        InstrumentAnalysisResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        InstrumentAnalysisResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(ScoreStatus.INSUFFICIENT_DATA);
        assertThat(body.finalScore()).isNull();
        assertThat(body.regime()).isEqualTo(ScoreRegime.INSUFFICIENT_DATA);
        assertThat(body.insufficientDataReasons()).isNotEmpty();

        String status =
                jdbcTemplate.queryForObject(
                        "SELECT status FROM technical_score_runs WHERE id = ?",
                        String.class,
                        body.scoreRunId());
        BigDecimal persistedScore =
                jdbcTemplate.queryForObject(
                        "SELECT final_score FROM technical_score_runs WHERE id = ?",
                        BigDecimal.class,
                        body.scoreRunId());
        assertThat(status).isEqualTo("INSUFFICIENT_DATA");
        assertThat(persistedScore).isNull();
    }

    @Test
    void screenerFiltersSortsPaginatesAndValidatesInputs() {
        HttpHeaders headers = registerAndLogin("scoring.screener@example.com");

        ResponseEntity<ScreenerPageResponse> allResponse =
                restTemplate.exchange(
                        "/api/v1/screener?exchange=BIST&page=0&size=20&sortBy=score&sortDir=desc",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        ScreenerPageResponse.class);

        assertThat(allResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        ScreenerPageResponse allPage = allResponse.getBody();
        assertThat(allPage).isNotNull();
        assertThat(allPage.totalElements()).isEqualTo(30L);
        assertThat(allPage.items()).hasSize(20);
        assertThat(allPage.dataSource().simulated()).isTrue();
        assertThat(allPage.freshness().status()).isEqualTo(FreshnessStatus.SIMULATED_ONLY);
        assertThat(allPage.items())
                .extracting(ScreenerItemResponse::finalScore)
                .isSortedAccordingTo(Comparator.reverseOrder());

        ResponseEntity<ScreenerPageResponse> pageZero =
                restTemplate.exchange(
                        "/api/v1/screener?exchange=BIST&page=0&size=5&sortBy=score&sortDir=desc",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        ScreenerPageResponse.class);
        assertThat(pageZero.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pageZero.getBody().items()).hasSize(5);
        assertThat(pageZero.getBody().totalPages()).isEqualTo(6);

        ResponseEntity<ScreenerPageResponse> queried =
                restTemplate.exchange(
                        "/api/v1/screener?exchange=BIST&query=PGSUS&includeInsufficientData=true",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        ScreenerPageResponse.class);
        assertThat(queried.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(queried.getBody().items())
                .extracting(ScreenerItemResponse::symbol)
                .contains("PGSUS");

        ScreenerItemResponse sample = allPage.items().get(2);
        BigDecimal filterMin = sample.finalScore();
        ResponseEntity<ScreenerPageResponse> filtered =
                restTemplate.exchange(
                        "/api/v1/screener?exchange=BIST&minScore="
                                + filterMin
                                + "&maxScore=100&rsiMax=100&trend="
                                + sample.trendDirection().name(),
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        ScreenerPageResponse.class);
        assertThat(filtered.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<ScreenerItemResponse> filteredItems = filtered.getBody().items();
        assertThat(filteredItems).isNotEmpty();
        assertThat(filteredItems)
                .allMatch(
                        item ->
                                item.finalScore().compareTo(filterMin) >= 0
                                        && item.trendDirection() == sample.trendDirection());

        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/screener?minScore=80&maxScore=20",
                                        HttpMethod.GET,
                                        new HttpEntity<>(headers),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/screener?size=500",
                                        HttpMethod.GET,
                                        new HttpEntity<>(headers),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/screener?trend=INVALID",
                                        HttpMethod.GET,
                                        new HttpEntity<>(headers),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/screener?scope=BAD",
                                        HttpMethod.GET,
                                        new HttpEntity<>(headers),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/screener?exchange=UNKNOWN",
                                        HttpMethod.GET,
                                        new HttpEntity<>(headers),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(
                        restTemplate
                                .getForEntity("/api/v1/screener", ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> openApi = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(openApi.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(openApi.getBody())
                .contains("/api/v1/instruments/{symbol}/analysis")
                .contains("/api/v1/screener");
    }

    private HttpHeaders registerAndLogin(String email) {
        restTemplate.postForEntity(
                "/api/v1/auth/register",
                new RegisterRequest(email, "ScoringTestPass123", "Scoring Tester", "Europe/Istanbul"),
                RegisterResponse.class);
        LoginResponse login =
                restTemplate
                        .postForEntity(
                                "/api/v1/auth/login",
                                new LoginRequest(email, "ScoringTestPass123"),
                                LoginResponse.class)
                        .getBody();
        assertThat(login).isNotNull();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.accessToken());
        return headers;
    }
}
