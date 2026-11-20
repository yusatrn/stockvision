package com.stockvision.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockvision.TestSecuritySupport;
import com.stockvision.auth.api.LoginRequest;
import com.stockvision.auth.api.LoginResponse;
import com.stockvision.auth.api.RegisterRequest;
import com.stockvision.auth.api.RegisterResponse;
import com.stockvision.common.api.ApiErrorResponse;
import com.stockvision.marketdata.api.BatchSyncResponse;
import com.stockvision.marketdata.api.UniverseSyncStatusResponse;
import com.stockvision.marketdata.domain.FreshnessStatus;
import com.stockvision.scoring.api.InstrumentSetupResponse;
import com.stockvision.scoring.api.OpportunitiesPageResponse;
import com.stockvision.scoring.api.OpportunityItemResponse;
import com.stockvision.scoring.domain.DeterministicTechnicalSetupEngine;
import com.stockvision.scoring.domain.ScoreStatus;
import com.stockvision.scoring.domain.SetupCategory;
import java.math.BigDecimal;
import java.util.Comparator;
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
class OpportunitiesIntegrationTest {

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
    void listsOpportunitiesAnalyzesSingleSetupAndValidatesInputs() {
        HttpHeaders headers = registerAndLogin("opportunities.tester@example.com");

        ResponseEntity<OpportunitiesPageResponse> listResponse =
                restTemplate.exchange(
                        "/api/v1/opportunities?exchange=BIST&interval=1d&sortBy=momentum&sortDir=desc",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        OpportunitiesPageResponse.class);

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        OpportunitiesPageResponse page = listResponse.getBody();
        assertThat(page).isNotNull();
        assertThat(page.setupEngineVersion())
                .isEqualTo(DeterministicTechnicalSetupEngine.ENGINE_VERSION);
        assertThat(page.totalElements()).isEqualTo(30L);
        assertThat(page.items()).hasSize(20);
        assertThat(page.disclaimer()).contains("tavan tahmini");
        assertThat(page.dataSource().simulated()).isTrue();
        assertThat(page.freshness().status()).isEqualTo(FreshnessStatus.SIMULATED_ONLY);
        long categorySum =
                page.categoryCounts().values().stream().mapToLong(Long::longValue).sum();
        assertThat(categorySum).isEqualTo(30L);

        assertThat(page.items())
                .extracting(OpportunityItemResponse::momentumBreakoutScore)
                .isSortedAccordingTo(Comparator.reverseOrder());

        for (OpportunityItemResponse item : page.items()) {
            if (item.status() == ScoreStatus.READY) {
                BigDecimal ref = item.technicalReferencePrice();
                assertThat(item.supportWatchZone()).isNotNull();
                assertThat(item.resistanceExitZone()).isNotNull();
                assertThat(item.invalidationRiskLevel()).isNotNull().isLessThan(ref);
                assertThat(item.supportWatchZone().low())
                        .isPositive()
                        .isLessThanOrEqualTo(item.supportWatchZone().high());
                assertThat(item.supportWatchZone().high()).isLessThanOrEqualTo(ref);
                assertThat(item.resistanceExitZone().low()).isGreaterThan(ref);
                assertThat(item.resistanceExitZone().high())
                        .isGreaterThan(item.resistanceExitZone().low());
                assertThat(item.bistMargin()).isNotNull();
                assertThat(item.riskReward()).isNotNull();
            }
        }

        ResponseEntity<InstrumentSetupResponse> singleSetup =
                restTemplate.exchange(
                        "/api/v1/instruments/THYAO/setups?interval=1d",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        InstrumentSetupResponse.class);
        assertThat(singleSetup.getStatusCode()).isEqualTo(HttpStatus.OK);
        InstrumentSetupResponse thyao = singleSetup.getBody();
        assertThat(thyao).isNotNull();
        assertThat(thyao.symbol()).isEqualTo("THYAO");
        assertThat(thyao.status()).isEqualTo(ScoreStatus.READY);
        assertThat(thyao.usedIndicators()).contains("ATR(14)", "RSI(14)", "BOLLINGER(20,2)");
        assertThat(thyao.supportWatchZone()).isNotNull();
        assertThat(thyao.resistanceExitZone()).isNotNull();
        assertThat(thyao.invalidationRiskLevel()).isNotNull();
        assertThat(thyao.bistMargin()).isNotNull();
        assertThat(thyao.riskReward()).isNotNull();

        ResponseEntity<InstrumentSetupResponse> insufficient =
                restTemplate.exchange(
                        "/api/v1/instruments/GARAN/setups?interval=1d"
                                + "&from=2026-11-09T00:00:00Z&to=2026-11-13T18:00:00Z",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        InstrumentSetupResponse.class);
        assertThat(insufficient.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(insufficient.getBody()).isNotNull();
        assertThat(insufficient.getBody().status()).isEqualTo(ScoreStatus.INSUFFICIENT_DATA);
        assertThat(insufficient.getBody().category()).isEqualTo(SetupCategory.INSUFFICIENT_DATA);
        assertThat(insufficient.getBody().supportWatchZone()).isNull();
        assertThat(insufficient.getBody().resistanceExitZone()).isNull();
        assertThat(insufficient.getBody().invalidationRiskLevel()).isNull();
        assertThat(insufficient.getBody().insufficientDataReasons()).isNotEmpty();

        assertThat(
                        restTemplate
                                .getForEntity("/api/v1/opportunities", ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/opportunities?size=101",
                                        HttpMethod.GET,
                                        new HttpEntity<>(headers),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/opportunities?category=INVALID",
                                        HttpMethod.GET,
                                        new HttpEntity<>(headers),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/opportunities?sortBy=bad",
                                        HttpMethod.GET,
                                        new HttpEntity<>(headers),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/opportunities?exchange=UNKNOWN",
                                        HttpMethod.GET,
                                        new HttpEntity<>(headers),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/opportunities?scope=BAD",
                                        HttpMethod.GET,
                                        new HttpEntity<>(headers),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<InstrumentSetupResponse> pgsusSetup =
                restTemplate.exchange(
                        "/api/v1/instruments/PGSUS/setups?interval=1d",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        InstrumentSetupResponse.class);
        assertThat(pgsusSetup.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pgsusSetup.getBody()).isNotNull();
        assertThat(pgsusSetup.getBody().symbol()).isEqualTo("PGSUS");
        assertThat(pgsusSetup.getBody().status()).isEqualTo(ScoreStatus.READY);

        ResponseEntity<OpportunitiesPageResponse> sasaSearch =
                restTemplate.exchange(
                        "/api/v1/opportunities?exchange=BIST&query=SASA",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        OpportunitiesPageResponse.class);
        assertThat(sasaSearch.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sasaSearch.getBody()).isNotNull();
        assertThat(sasaSearch.getBody().items())
                .extracting(OpportunityItemResponse::symbol)
                .contains("SASA");

        ResponseEntity<UniverseSyncStatusResponse> syncBefore =
                restTemplate.exchange(
                        "/api/v1/market-data/sync-status?exchange=BIST&interval=1d",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        UniverseSyncStatusResponse.class);
        assertThat(syncBefore.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(syncBefore.getBody()).isNotNull();
        assertThat(syncBefore.getBody().totalActiveInstruments()).isGreaterThanOrEqualTo(500);
        int syncedBefore = syncBefore.getBody().syncedInstruments();

        ResponseEntity<BatchSyncResponse> batchSync =
                restTemplate.exchange(
                        "/api/v1/market-data/sync-batch?exchange=BIST&interval=1d&batchSize=5",
                        HttpMethod.POST,
                        new HttpEntity<>(headers),
                        BatchSyncResponse.class);
        assertThat(batchSync.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(batchSync.getBody()).isNotNull();
        assertThat(batchSync.getBody().processedInBatch()).isEqualTo(5);
        assertThat(batchSync.getBody().succeededInBatch()).isEqualTo(5);
        assertThat(batchSync.getBody().syncedInstruments()).isEqualTo(syncedBefore + 5);

        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/market-data/sync-batch?batchSize=51",
                                        HttpMethod.POST,
                                        new HttpEntity<>(headers),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> openApi = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(openApi.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(openApi.getBody())
                .contains("/api/v1/opportunities")
                .contains("/api/v1/instruments/{symbol}/setups");
    }

    private HttpHeaders registerAndLogin(String email) {
        restTemplate.postForEntity(
                "/api/v1/auth/register",
                new RegisterRequest(
                        email, "OppTestPass123", "Opportunities Tester", "Europe/Istanbul"),
                RegisterResponse.class);
        LoginResponse login =
                restTemplate
                        .postForEntity(
                                "/api/v1/auth/login",
                                new LoginRequest(email, "OppTestPass123"),
                                LoginResponse.class)
                        .getBody();
        assertThat(login).isNotNull();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.accessToken());
        return headers;
    }
}
