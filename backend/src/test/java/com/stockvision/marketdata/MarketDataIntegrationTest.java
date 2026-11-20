package com.stockvision.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockvision.TestSecuritySupport;
import com.stockvision.auth.api.LoginRequest;
import com.stockvision.auth.api.LoginResponse;
import com.stockvision.auth.api.RegisterRequest;
import com.stockvision.auth.api.RegisterResponse;
import com.stockvision.common.api.ApiErrorResponse;
import com.stockvision.market.api.ExchangeResponse;
import com.stockvision.market.api.MarketSummaryResponse;
import com.stockvision.marketdata.api.HistoricalBarsResponse;
import com.stockvision.marketdata.api.SyncBarsRequest;
import com.stockvision.marketdata.api.SyncResultResponse;
import com.stockvision.marketdata.domain.DataProviderMode;
import com.stockvision.marketdata.domain.FreshnessStatus;
import com.stockvision.marketdata.domain.SyncRunStatus;
import com.stockvision.stock.api.InstrumentDetailResponse;
import com.stockvision.stock.api.InstrumentPageResponse;
import java.time.Instant;
import java.util.Arrays;
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
class MarketDataIntegrationTest {

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
    void unauthenticatedRequestsReturnUnauthorized() {
        assertThat(restTemplate.getForEntity("/api/v1/markets", ApiErrorResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(
                        restTemplate
                                .getForEntity("/api/v1/instruments", ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(
                        restTemplate
                                .getForEntity(
                                        "/api/v1/instruments/THYAO/bars", ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void marketsAndSummaryExposeSimulatedMetadata() {
        HttpHeaders userHeaders = registerAndLogin("market.viewer@example.com", false);

        ResponseEntity<ExchangeResponse[]> marketsResponse =
                restTemplate.exchange(
                        "/api/v1/markets",
                        HttpMethod.GET,
                        new HttpEntity<>(userHeaders),
                        ExchangeResponse[].class);
        assertThat(marketsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<ExchangeResponse> markets = Arrays.asList(marketsResponse.getBody());
        assertThat(markets)
                .extracting(ExchangeResponse::code)
                .contains("BIST");

        ResponseEntity<MarketSummaryResponse> summaryResponse =
                restTemplate.exchange(
                        "/api/v1/markets/summary?exchange=BIST",
                        HttpMethod.GET,
                        new HttpEntity<>(userHeaders),
                        MarketSummaryResponse.class);
        assertThat(summaryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        MarketSummaryResponse summary = summaryResponse.getBody();
        assertThat(summary).isNotNull();
        assertThat(summary.simulated()).isTrue();
        assertThat(summary.activeInstrumentCount()).isGreaterThanOrEqualTo(500L);
        assertThat(summary.dataSource().mode()).isEqualTo(DataProviderMode.SIMULATED);
        assertThat(summary.freshness().status()).isEqualTo(FreshnessStatus.SIMULATED_ONLY);
        assertThat(summary.movers()).hasSize(10);
        assertThat(summary.movers()).allMatch(mover -> mover.simulated());
    }

    @Test
    void instrumentSearchDetailAndPaginationValidation() {
        HttpHeaders userHeaders = registerAndLogin("instrument.viewer@example.com", false);

        ResponseEntity<InstrumentPageResponse> searchResponse =
                restTemplate.exchange(
                        "/api/v1/instruments?query=thy&exchange=BIST&page=0&size=5",
                        HttpMethod.GET,
                        new HttpEntity<>(userHeaders),
                        InstrumentPageResponse.class);
        assertThat(searchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(searchResponse.getBody()).isNotNull();
        assertThat(searchResponse.getBody().items()).hasSize(1);
        assertThat(searchResponse.getBody().items().getFirst().symbol()).isEqualTo("THYAO");
        assertThat(searchResponse.getBody().dataSource().simulated()).isTrue();

        ResponseEntity<InstrumentPageResponse> expandedSearch =
                restTemplate.exchange(
                        "/api/v1/instruments?query=PGSUS&exchange=BIST&page=0&size=5",
                        HttpMethod.GET,
                        new HttpEntity<>(userHeaders),
                        InstrumentPageResponse.class);
        assertThat(expandedSearch.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(expandedSearch.getBody()).isNotNull();
        assertThat(expandedSearch.getBody().items())
                .extracting(item -> item.symbol())
                .contains("PGSUS");

        ResponseEntity<ApiErrorResponse> badPage =
                restTemplate.exchange(
                        "/api/v1/instruments?page=-1&size=20",
                        HttpMethod.GET,
                        new HttpEntity<>(userHeaders),
                        ApiErrorResponse.class);
        assertThat(badPage.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badPage.getBody().code()).isEqualTo("VALIDATION_ERROR");

        ResponseEntity<ApiErrorResponse> excessiveSize =
                restTemplate.exchange(
                        "/api/v1/instruments?page=0&size=500",
                        HttpMethod.GET,
                        new HttpEntity<>(userHeaders),
                        ApiErrorResponse.class);
        assertThat(excessiveSize.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(excessiveSize.getBody().code()).isEqualTo("VALIDATION_ERROR");

        ResponseEntity<InstrumentDetailResponse> detailResponse =
                restTemplate.exchange(
                        "/api/v1/instruments/thyao",
                        HttpMethod.GET,
                        new HttpEntity<>(userHeaders),
                        InstrumentDetailResponse.class);
        assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detailResponse.getBody()).isNotNull();
        assertThat(detailResponse.getBody().symbol()).isEqualTo("THYAO");
        assertThat(detailResponse.getBody().latestQuote().simulated()).isTrue();
        assertThat(detailResponse.getBody().freshness().status())
                .isEqualTo(FreshnessStatus.SIMULATED_ONLY);

        ResponseEntity<ApiErrorResponse> missingSymbol =
                restTemplate.exchange(
                        "/api/v1/instruments/UNKNOWN999",
                        HttpMethod.GET,
                        new HttpEntity<>(userHeaders),
                        ApiErrorResponse.class);
        assertThat(missingSymbol.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void historicalBarsIngestIdempotentlySeparateAdjustedAndEnforceAdminRole() {
        HttpHeaders userHeaders = registerAndLogin("bars.user@example.com", false);
        HttpHeaders adminHeaders = registerAndLogin("bars.admin@example.com", true);

        Instant from = Instant.parse("2025-06-15T00:00:00Z");
        Instant to = Instant.parse("2025-07-10T23:59:59Z");

        ResponseEntity<HistoricalBarsResponse> unadjustedResponse =
                restTemplate.exchange(
                        "/api/v1/instruments/THYAO/bars?interval=1d&from="
                                + from
                                + "&to="
                                + to
                                + "&adjusted=false",
                        HttpMethod.GET,
                        new HttpEntity<>(userHeaders),
                        HistoricalBarsResponse.class);
        assertThat(unadjustedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        HistoricalBarsResponse unadjusted = unadjustedResponse.getBody();
        assertThat(unadjusted).isNotNull();
        assertThat(unadjusted.bars()).isNotEmpty();
        assertThat(unadjusted.adjusted()).isFalse();
        assertThat(unadjusted.dataSource().simulated()).isTrue();
        assertThat(unadjusted.freshness().status()).isEqualTo(FreshnessStatus.SIMULATED_ONLY);
        assertThat(unadjusted.freshness().lastSyncStatus()).isEqualTo(SyncRunStatus.SUCCESS);
        assertThat(unadjusted.bars()).allMatch(bar -> bar.simulated() && !bar.adjusted());

        Integer countAfterFirstIngest =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM market_data_bars b
                        JOIN instruments i ON i.id = b.instrument_id
                        WHERE i.symbol = 'THYAO' AND b.bar_interval = '1d' AND b.adjusted = FALSE
                        """,
                        Integer.class);
        assertThat(countAfterFirstIngest).isEqualTo(unadjusted.bars().size());

        SyncBarsRequest syncRequest = new SyncBarsRequest("THYAO", "1d", from, to, false);
        ResponseEntity<ApiErrorResponse> forbiddenSync =
                restTemplate.exchange(
                        "/api/v1/market-data/sync",
                        HttpMethod.POST,
                        new HttpEntity<>(syncRequest, userHeaders),
                        ApiErrorResponse.class);
        assertThat(forbiddenSync.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbiddenSync.getBody().code()).isEqualTo("FORBIDDEN");

        ResponseEntity<SyncResultResponse> adminResync =
                restTemplate.exchange(
                        "/api/v1/market-data/sync",
                        HttpMethod.POST,
                        new HttpEntity<>(syncRequest, adminHeaders),
                        SyncResultResponse.class);
        assertThat(adminResync.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adminResync.getBody()).isNotNull();
        assertThat(adminResync.getBody().status()).isEqualTo(SyncRunStatus.SUCCESS);
        assertThat(adminResync.getBody().barsInserted()).isZero();
        assertThat(adminResync.getBody().barsSkipped())
                .isEqualTo(adminResync.getBody().barsFetched());

        Integer countAfterResync =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM market_data_bars b
                        JOIN instruments i ON i.id = b.instrument_id
                        WHERE i.symbol = 'THYAO' AND b.bar_interval = '1d' AND b.adjusted = FALSE
                        """,
                        Integer.class);
        assertThat(countAfterResync).isEqualTo(countAfterFirstIngest);

        ResponseEntity<HistoricalBarsResponse> adjustedResponse =
                restTemplate.exchange(
                        "/api/v1/instruments/THYAO/bars?interval=1d&from="
                                + from
                                + "&to="
                                + to
                                + "&adjusted=true",
                        HttpMethod.GET,
                        new HttpEntity<>(userHeaders),
                        HistoricalBarsResponse.class);
        assertThat(adjustedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        HistoricalBarsResponse adjusted = adjustedResponse.getBody();
        assertThat(adjusted).isNotNull();
        assertThat(adjusted.adjusted()).isTrue();
        assertThat(adjusted.bars()).hasSameSizeAs(unadjusted.bars());
        assertThat(adjusted.bars().getFirst().close())
                .isLessThan(unadjusted.bars().getFirst().close());

        ResponseEntity<ApiErrorResponse> badInterval =
                restTemplate.exchange(
                        "/api/v1/instruments/THYAO/bars?interval=5y",
                        HttpMethod.GET,
                        new HttpEntity<>(userHeaders),
                        ApiErrorResponse.class);
        assertThat(badInterval.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badInterval.getBody().code()).isEqualTo("VALIDATION_ERROR");

        ResponseEntity<ApiErrorResponse> invertedRange =
                restTemplate.exchange(
                        "/api/v1/instruments/THYAO/bars?interval=1d&from="
                                + to
                                + "&to="
                                + from,
                        HttpMethod.GET,
                        new HttpEntity<>(userHeaders),
                        ApiErrorResponse.class);
        assertThat(invertedRange.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<ApiErrorResponse> excessiveRange =
                restTemplate.exchange(
                        "/api/v1/instruments/THYAO/bars?interval=15m&from=2026-08-01T00:00:00Z&to=2026-11-01T00:00:00Z",
                        HttpMethod.GET,
                        new HttpEntity<>(userHeaders),
                        ApiErrorResponse.class);
        assertThat(excessiveRange.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void openApiDocumentsMarketAndInstrumentEndpoints() {
        ResponseEntity<String> openApi = restTemplate.getForEntity("/v3/api-docs", String.class);

        assertThat(openApi.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(openApi.getBody())
                .contains("/api/v1/markets")
                .contains("/api/v1/markets/summary")
                .contains("/api/v1/instruments")
                .contains("/api/v1/instruments/{symbol}")
                .contains("/api/v1/instruments/{symbol}/bars")
                .contains("/api/v1/market-data/sync")
                .contains("/api/v1/market-data/sync-status")
                .contains("/api/v1/market-data/sync-batch");
    }

    private HttpHeaders registerAndLogin(String email, boolean admin) {
        restTemplate.postForEntity(
                "/api/v1/auth/register",
                new RegisterRequest(email, "MarketTestPass123", "Tester", "Europe/Istanbul"),
                RegisterResponse.class);
        if (admin) {
            jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", email);
        }
        LoginResponse login =
                restTemplate
                        .postForEntity(
                                "/api/v1/auth/login",
                                new LoginRequest(email, "MarketTestPass123"),
                                LoginResponse.class)
                        .getBody();
        assertThat(login).isNotNull();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.accessToken());
        return headers;
    }
}
