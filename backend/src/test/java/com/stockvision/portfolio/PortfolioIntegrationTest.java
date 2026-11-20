package com.stockvision.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockvision.TestSecuritySupport;
import com.stockvision.auth.api.LoginRequest;
import com.stockvision.auth.api.LoginResponse;
import com.stockvision.auth.api.RegisterRequest;
import com.stockvision.auth.api.RegisterResponse;
import com.stockvision.common.api.ApiErrorResponse;
import com.stockvision.portfolio.api.CreatePortfolioRequest;
import com.stockvision.portfolio.api.CreateTransactionRequest;
import com.stockvision.portfolio.api.PortfolioPerformanceResponse;
import com.stockvision.portfolio.api.PortfolioSummaryResponse;
import com.stockvision.portfolio.api.PortfolioTransactionResponse;
import com.stockvision.portfolio.domain.FeeTaxPolicy;
import com.stockvision.portfolio.domain.PortfolioValuationStatus;
import com.stockvision.portfolio.domain.TransactionSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
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
class PortfolioIntegrationTest {

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
    void derivesCostRealizedAndUnrealizedPnlAndEnforcesLedgerAndOwnershipRules() {
        HttpHeaders userA = registerAndLogin("portfolio.a@example.com");
        HttpHeaders userB = registerAndLogin("portfolio.b@example.com");

        assertThat(restTemplate.getForEntity("/api/v1/portfolios", ApiErrorResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // Create EXCLUDED_MVP portfolio
        ResponseEntity<PortfolioSummaryResponse> mvpCreate =
                restTemplate.exchange(
                        "/api/v1/portfolios",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                new CreatePortfolioRequest(
                                        "Core TRY", "MVP Simulation", "TRY", FeeTaxPolicy.EXCLUDED_MVP),
                                userA),
                        PortfolioSummaryResponse.class);
        assertThat(mvpCreate.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PortfolioSummaryResponse mvpPortfolio = mvpCreate.getBody();
        assertThat(mvpPortfolio).isNotNull();

        // Empty portfolio reports EMPTY valuation status
        PortfolioPerformanceResponse emptyPerf =
                restTemplate
                        .exchange(
                                "/api/v1/portfolios/" + mvpPortfolio.id() + "/performance",
                                HttpMethod.GET,
                                new HttpEntity<>(userA),
                                PortfolioPerformanceResponse.class)
                        .getBody();
        assertThat(emptyPerf).isNotNull();
        assertThat(emptyPerf.valuationStatus()).isEqualTo(PortfolioValuationStatus.EMPTY);

        // Fees rejected under EXCLUDED_MVP policy
        ResponseEntity<ApiErrorResponse> rejectedFee =
                restTemplate.exchange(
                        "/api/v1/portfolios/" + mvpPortfolio.id() + "/transactions",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                new CreateTransactionRequest(
                                        "THYAO",
                                        TransactionSide.BUY,
                                        new BigDecimal("10"),
                                        new BigDecimal("200.00"),
                                        new BigDecimal("5.00"),
                                        BigDecimal.ZERO,
                                        Instant.parse("2026-11-01T10:00:00Z"),
                                        null),
                                userA),
                        ApiErrorResponse.class);
        assertThat(rejectedFee.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Add BUY 10 @ 100.00 and BUY 10 @ 140.00 -> avg cost 120.0000
        ResponseEntity<PortfolioTransactionResponse> buy1 =
                restTemplate.exchange(
                        "/api/v1/portfolios/" + mvpPortfolio.id() + "/transactions",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                new CreateTransactionRequest(
                                        "THYAO",
                                        TransactionSide.BUY,
                                        new BigDecimal("10"),
                                        new BigDecimal("100.00"),
                                        BigDecimal.ZERO,
                                        BigDecimal.ZERO,
                                        Instant.parse("2026-11-01T10:00:00Z"),
                                        "Initial tranche"),
                                userA),
                        PortfolioTransactionResponse.class);
        assertThat(buy1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        restTemplate.exchange(
                "/api/v1/portfolios/" + mvpPortfolio.id() + "/transactions",
                HttpMethod.POST,
                new HttpEntity<>(
                        new CreateTransactionRequest(
                                "THYAO",
                                TransactionSide.BUY,
                                new BigDecimal("10"),
                                new BigDecimal("140.00"),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                Instant.parse("2026-11-02T10:00:00Z"),
                                "Second tranche"),
                        userA),
                PortfolioTransactionResponse.class);

        // Oversell (25 > 20) rejected
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/portfolios/" + mvpPortfolio.id() + "/transactions",
                                        HttpMethod.POST,
                                        new HttpEntity<>(
                                                new CreateTransactionRequest(
                                                        "THYAO",
                                                        TransactionSide.SELL,
                                                        new BigDecimal("25"),
                                                        new BigDecimal("150.00"),
                                                        BigDecimal.ZERO,
                                                        BigDecimal.ZERO,
                                                        Instant.parse("2026-11-03T10:00:00Z"),
                                                        null),
                                                userA),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Partial SELL 12 @ 150.00 -> realized = (150 - 120) * 12 = +360.00; remaining 8 @ 120 = 960.00
        restTemplate.exchange(
                "/api/v1/portfolios/" + mvpPortfolio.id() + "/transactions",
                HttpMethod.POST,
                new HttpEntity<>(
                        new CreateTransactionRequest(
                                "THYAO",
                                TransactionSide.SELL,
                                new BigDecimal("12"),
                                new BigDecimal("150.00"),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                Instant.parse("2026-11-03T10:00:00Z"),
                                "Trim"),
                        userA),
                PortfolioTransactionResponse.class);

        // Deleting first BUY (10 shares) would leave BUY 10 < SELL 12 -> 409 CONFLICT
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/portfolios/"
                                                + mvpPortfolio.id()
                                                + "/transactions/"
                                                + buy1.getBody().id(),
                                        HttpMethod.DELETE,
                                        new HttpEntity<>(userA),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        // Verify derived performance response
        PortfolioPerformanceResponse perf =
                restTemplate
                        .exchange(
                                "/api/v1/portfolios/" + mvpPortfolio.id() + "/performance",
                                HttpMethod.GET,
                                new HttpEntity<>(userA),
                                PortfolioPerformanceResponse.class)
                        .getBody();
        assertThat(perf).isNotNull();
        assertThat(perf.valuationStatus()).isEqualTo(PortfolioValuationStatus.READY);
        assertThat(perf.totalCostBasis()).isEqualByComparingTo("960.00");
        assertThat(perf.totalRealizedPnl()).isEqualByComparingTo("360.00");
        assertThat(perf.openPositionsCount()).isEqualTo(1);
        assertThat(perf.holdings()).hasSize(1);
        assertThat(perf.holdings().get(0).openQuantity()).isEqualByComparingTo("8.000000");
        assertThat(perf.holdings().get(0).averageCost()).isEqualByComparingTo("120.0000");
        assertThat(perf.holdings().get(0).latestPrice()).isPositive();
        assertThat(perf.holdings().get(0).marketValue()).isNotNull();
        assertThat(perf.holdings().get(0).unrealizedPnl()).isNotNull();
        assertThat(perf.dataSource().simulated()).isTrue();
        assertThat(perf.disclaimer()).contains("karar destek");

        // Verify listTransactions returns 3 chronological rows
        ResponseEntity<List<PortfolioTransactionResponse>> txns =
                restTemplate.exchange(
                        "/api/v1/portfolios/" + mvpPortfolio.id() + "/transactions",
                        HttpMethod.GET,
                        new HttpEntity<>(userA),
                        new ParameterizedTypeReference<>() {});
        assertThat(txns.getBody()).hasSize(3);

        // Ownership checks: User B blocked with 404 on every endpoint targeting User A's portfolio
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/portfolios/" + mvpPortfolio.id() + "/performance",
                                        HttpMethod.GET,
                                        new HttpEntity<>(userB),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/portfolios/" + mvpPortfolio.id() + "/transactions",
                                        HttpMethod.GET,
                                        new HttpEntity<>(userB),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/portfolios/" + mvpPortfolio.id(),
                                        HttpMethod.DELETE,
                                        new HttpEntity<>(userB),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private HttpHeaders registerAndLogin(String email) {
        restTemplate.postForEntity(
                "/api/v1/auth/register",
                new RegisterRequest(email, "PortfolioPass123", "Portfolio Tester", "Europe/Istanbul"),
                RegisterResponse.class);
        LoginResponse login =
                restTemplate
                        .postForEntity(
                                "/api/v1/auth/login",
                                new LoginRequest(email, "PortfolioPass123"),
                                LoginResponse.class)
                        .getBody();
        assertThat(login).isNotNull();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.accessToken());
        return headers;
    }
}
