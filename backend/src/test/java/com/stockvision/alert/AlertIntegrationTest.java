package com.stockvision.alert;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockvision.TestSecuritySupport;
import com.stockvision.alert.api.AlertEvaluationRunResponse;
import com.stockvision.alert.api.AlertNotificationResponse;
import com.stockvision.alert.api.AlertRuleListResponse;
import com.stockvision.alert.api.AlertRuleResponse;
import com.stockvision.alert.api.CreateAlertRequest;
import com.stockvision.alert.api.NotificationPageResponse;
import com.stockvision.alert.api.UpdateAlertRequest;
import com.stockvision.alert.domain.AlertConditionType;
import com.stockvision.alert.domain.AlertStatus;
import com.stockvision.alert.domain.AlertTriggerState;
import com.stockvision.alert.domain.EvaluationOutcomeType;
import com.stockvision.auth.api.LoginRequest;
import com.stockvision.auth.api.LoginResponse;
import com.stockvision.auth.api.RegisterRequest;
import com.stockvision.auth.api.RegisterResponse;
import com.stockvision.common.api.ApiErrorResponse;
import com.stockvision.marketdata.domain.FreshnessStatus;
import com.stockvision.technicalanalysis.domain.IndicatorType;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.http.client.JdkClientHttpRequestFactory;
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
class AlertIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        TestSecuritySupport.registerJwtSecret(registry);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void configurePatchSupport() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void evaluatesPriceAndIndicatorAlertsIdempotentlyWithCooldownAndOwnershipIsolation() {
        HttpHeaders userA = registerAndLogin("alert.a@example.com");
        HttpHeaders userB = registerAndLogin("alert.b@example.com");

        assertThat(restTemplate.getForEntity("/api/v1/alerts", ApiErrorResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // Invalid indicator field fails validation cleanly
        ResponseEntity<ApiErrorResponse> invalidIndicator =
                restTemplate.exchange(
                        "/api/v1/alerts",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                new CreateAlertRequest(
                                        "THYAO",
                                        AlertConditionType.INDICATOR_ABOVE,
                                        IndicatorType.RSI,
                                        "nonexistent",
                                        "1d",
                                        new BigDecimal("50"),
                                        60,
                                        null),
                                userA),
                        ApiErrorResponse.class);
        assertThat(invalidIndicator.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Create price alert guaranteed to match mock price (>= 1.00)
        ResponseEntity<AlertRuleResponse> priceCreate =
                restTemplate.exchange(
                        "/api/v1/alerts",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                new CreateAlertRequest(
                                        "THYAO",
                                        AlertConditionType.PRICE_ABOVE,
                                        null,
                                        null,
                                        "1d",
                                        new BigDecimal("1.00"),
                                        60,
                                        "Breakout watch"),
                                userA),
                        AlertRuleResponse.class);
        assertThat(priceCreate.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AlertRuleResponse priceAlert = priceCreate.getBody();
        assertThat(priceAlert).isNotNull();

        // Create RSI indicator alert guaranteed to match (>= 0)
        ResponseEntity<AlertRuleResponse> rsiCreate =
                restTemplate.exchange(
                        "/api/v1/alerts",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                new CreateAlertRequest(
                                        "GARAN",
                                        AlertConditionType.INDICATOR_ABOVE,
                                        IndicatorType.RSI,
                                        "value",
                                        "1d",
                                        new BigDecimal("-5.00"),
                                        60,
                                        "RSI check"),
                                userA),
                        AlertRuleResponse.class);
        assertThat(rsiCreate.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // First evaluation triggers both rules and records provider/freshness metadata
        ResponseEntity<AlertEvaluationRunResponse> firstEval =
                restTemplate.exchange(
                        "/api/v1/alerts/evaluate",
                        HttpMethod.POST,
                        new HttpEntity<>(userA),
                        AlertEvaluationRunResponse.class);
        assertThat(firstEval.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstEval.getBody().evaluatedCount()).isEqualTo(2);
        assertThat(firstEval.getBody().triggeredCount()).isEqualTo(2);

        // Second immediate evaluation suppresses both via cooldown/idempotency -> zero notification storm
        ResponseEntity<AlertEvaluationRunResponse> secondEval =
                restTemplate.exchange(
                        "/api/v1/alerts/evaluate",
                        HttpMethod.POST,
                        new HttpEntity<>(userA),
                        AlertEvaluationRunResponse.class);
        assertThat(secondEval.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondEval.getBody().triggeredCount()).isEqualTo(0);
        assertThat(secondEval.getBody().suppressedCount()).isEqualTo(2);
        assertThat(secondEval.getBody().results())
                .extracting(AlertEvaluationRunResponse.EvaluatedRuleResult::outcome)
                .containsOnly(EvaluationOutcomeType.COOLDOWN_SUPPRESSED);

        // Notifications endpoint contains exactly 2 notifications with provenance metadata
        NotificationPageResponse page =
                restTemplate
                        .exchange(
                                "/api/v1/notifications?unreadOnly=true&page=0&size=20",
                                HttpMethod.GET,
                                new HttpEntity<>(userA),
                                NotificationPageResponse.class)
                        .getBody();
        assertThat(page).isNotNull();
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.unreadCount()).isEqualTo(2);
        assertThat(page.items())
                .allSatisfy(
                        item -> {
                            assertThat(item.simulated()).isTrue();
                            assertThat(item.freshnessStatus())
                                    .isEqualTo(FreshnessStatus.SIMULATED_ONLY);
                            assertThat(item.providerDisplayName()).isNotBlank();
                            assertThat(item.read()).isFalse();
                        });

        // PATCH updates rule status and resets triggerState when resumed/re-tuned
        ResponseEntity<AlertRuleResponse> paused =
                restTemplate.exchange(
                        "/api/v1/alerts/" + priceAlert.id(),
                        HttpMethod.PATCH,
                        new HttpEntity<>(
                                new UpdateAlertRequest(
                                        AlertStatus.PAUSED, null, null, "Paused manually"),
                                userA),
                        AlertRuleResponse.class);
        assertThat(paused.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(paused.getBody().status()).isEqualTo(AlertStatus.PAUSED);

        ResponseEntity<AlertRuleResponse> resumed =
                restTemplate.exchange(
                        "/api/v1/alerts/" + priceAlert.id(),
                        HttpMethod.PATCH,
                        new HttpEntity<>(
                                new UpdateAlertRequest(AlertStatus.ACTIVE, null, null, null),
                                userA),
                        AlertRuleResponse.class);
        assertThat(resumed.getBody().status()).isEqualTo(AlertStatus.ACTIVE);
        assertThat(resumed.getBody().triggerState()).isEqualTo(AlertTriggerState.ARMED);

        // User B cannot read/update/delete User A's alerts or notifications
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/alerts/" + priceAlert.id(),
                                        HttpMethod.PATCH,
                                        new HttpEntity<>(
                                                new UpdateAlertRequest(
                                                        AlertStatus.PAUSED, null, null, null),
                                                userB),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/notifications/"
                                                + page.items().get(0).id()
                                                + "/read",
                                        HttpMethod.PATCH,
                                        new HttpEntity<>(userB),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/alerts",
                                        HttpMethod.GET,
                                        new HttpEntity<>(userB),
                                        AlertRuleListResponse.class)
                                .getBody()
                                .alerts())
                .isEmpty();

        // Mark single notification read and then remaining notifications read
        ResponseEntity<AlertNotificationResponse> readOne =
                restTemplate.exchange(
                        "/api/v1/notifications/" + page.items().get(0).id() + "/read",
                        HttpMethod.PATCH,
                        new HttpEntity<>(userA),
                        AlertNotificationResponse.class);
        assertThat(readOne.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readOne.getBody().read()).isTrue();

        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/notifications/read-all",
                                        HttpMethod.POST,
                                        new HttpEntity<>(userA),
                                        String.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        NotificationPageResponse afterRead =
                restTemplate
                        .exchange(
                                "/api/v1/notifications?unreadOnly=true",
                                HttpMethod.GET,
                                new HttpEntity<>(userA),
                                NotificationPageResponse.class)
                        .getBody();
        assertThat(afterRead.unreadCount()).isZero();
        assertThat(afterRead.items()).isEmpty();

        // Verify OpenAPI documents Phase 7 endpoints
        ResponseEntity<String> openApi = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(openApi.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(openApi.getBody())
                .contains("/api/v1/watchlists")
                .contains("/api/v1/portfolios")
                .contains("/api/v1/alerts")
                .contains("/api/v1/notifications");
    }

    private HttpHeaders registerAndLogin(String email) {
        restTemplate.postForEntity(
                "/api/v1/auth/register",
                new RegisterRequest(email, "AlertPass123", "Alert Tester", "Europe/Istanbul"),
                RegisterResponse.class);
        LoginResponse login =
                restTemplate
                        .postForEntity(
                                "/api/v1/auth/login",
                                new LoginRequest(email, "AlertPass123"),
                                LoginResponse.class)
                        .getBody();
        assertThat(login).isNotNull();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.accessToken());
        return headers;
    }
}
