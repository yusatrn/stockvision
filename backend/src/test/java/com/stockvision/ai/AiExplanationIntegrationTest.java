package com.stockvision.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockvision.TestSecuritySupport;
import com.stockvision.ai.api.AiExplanationResponse;
import com.stockvision.ai.domain.AiExplanationStatus;
import com.stockvision.ai.domain.AiFallbackReason;
import com.stockvision.ai.domain.AiPromptBuilder;
import com.stockvision.ai.infrastructure.DeterministicTemplateAiProvider;
import com.stockvision.auth.api.LoginRequest;
import com.stockvision.auth.api.LoginResponse;
import com.stockvision.auth.api.RegisterRequest;
import com.stockvision.auth.api.RegisterResponse;
import com.stockvision.common.api.ApiErrorResponse;
import com.stockvision.scoring.api.InstrumentAnalysisResponse;
import com.stockvision.scoring.domain.ScoreStatus;
import java.util.UUID;
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
class AiExplanationIntegrationTest {

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
    void generatesCachesRegeneratesAndHandlesInsufficientDataForAiExplanations() {
        assertThat(
                        restTemplate
                                .getForEntity(
                                        "/api/v1/instruments/THYAO/ai-explanation",
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders headers = registerAndLogin("ai.tester@example.com");

        // Compute persisted READY score run first
        InstrumentAnalysisResponse readyScore =
                restTemplate
                        .exchange(
                                "/api/v1/instruments/THYAO/analysis?interval=1d"
                                        + "&from=2026-06-01T00:00:00Z&to=2026-11-18T18:00:00Z",
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                InstrumentAnalysisResponse.class)
                        .getBody();
        assertThat(readyScore).isNotNull();
        assertThat(readyScore.status()).isEqualTo(ScoreStatus.READY);

        // Fetch AI explanation bound to persisted scoreRunId
        ResponseEntity<AiExplanationResponse> firstRes =
                restTemplate.exchange(
                        "/api/v1/instruments/THYAO/ai-explanation?interval=1d&scoreRunId="
                                + readyScore.scoreRunId(),
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        AiExplanationResponse.class);
        assertThat(firstRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        AiExplanationResponse body = firstRes.getBody();
        assertThat(body).isNotNull();
        assertThat(body.analysisRunId()).isEqualTo(readyScore.analysisRunId());
        assertThat(body.scoreRunId()).isEqualTo(readyScore.scoreRunId());
        assertThat(body.providerId()).isEqualTo(DeterministicTemplateAiProvider.PROVIDER_ID);
        assertThat(body.modelName()).isEqualTo(DeterministicTemplateAiProvider.MODEL_NAME);
        assertThat(body.promptVersion()).isEqualTo(AiPromptBuilder.PROMPT_VERSION);
        assertThat(body.status()).isEqualTo(AiExplanationStatus.GENERATED);
        assertThat(body.fallbackReason()).isEqualTo(AiFallbackReason.TEMPLATE_MODE_SELECTED);
        assertThat(body.simulatedData()).isTrue();
        assertThat(body.explanation().observedFacts()).isNotEmpty();
        assertThat(body.explanation().interpretation()).isNotEmpty();
        assertThat(body.explanation().uncertaintiesAndRisks()).isNotEmpty();
        assertThat(body.disclaimer()).contains("yatırım danışmanlığı değildir");

        // Verify persisted row in ai_explanations
        Integer rowCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ai_explanations WHERE id = ?",
                        Integer.class,
                        body.explanationId());
        assertThat(rowCount).isEqualTo(1);

        // Second GET returns cached explanationId without inserting another row
        AiExplanationResponse cachedRes =
                restTemplate
                        .exchange(
                                "/api/v1/instruments/THYAO/ai-explanation?interval=1d&scoreRunId="
                                        + readyScore.scoreRunId(),
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                AiExplanationResponse.class)
                        .getBody();
        assertThat(cachedRes).isNotNull();
        assertThat(cachedRes.explanationId()).isEqualTo(body.explanationId());

        // POST /regenerate persists a new explanation row
        ResponseEntity<AiExplanationResponse> regeneratedRes =
                restTemplate.exchange(
                        "/api/v1/instruments/THYAO/ai-explanation/regenerate?interval=1d&scoreRunId="
                                + readyScore.scoreRunId(),
                        HttpMethod.POST,
                        new HttpEntity<>(headers),
                        AiExplanationResponse.class);
        assertThat(regeneratedRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID regenId = regeneratedRes.getBody().explanationId();
        assertThat(regenId).isNotEqualTo(body.explanationId());

        // Narrow window yielding INSUFFICIENT_DATA score run -> AI explanation reflects INSUFFICIENT_DATA cleanly
        InstrumentAnalysisResponse narrowScore =
                restTemplate
                        .exchange(
                                "/api/v1/instruments/GARAN/analysis?interval=1d"
                                        + "&from=2026-11-10T00:00:00Z&to=2026-11-18T18:00:00Z",
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                InstrumentAnalysisResponse.class)
                        .getBody();
        assertThat(narrowScore).isNotNull();
        assertThat(narrowScore.status()).isEqualTo(ScoreStatus.INSUFFICIENT_DATA);

        AiExplanationResponse insufficientAi =
                restTemplate
                        .exchange(
                                "/api/v1/instruments/GARAN/ai-explanation?interval=1d&scoreRunId="
                                        + narrowScore.scoreRunId(),
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                AiExplanationResponse.class)
                        .getBody();
        assertThat(insufficientAi).isNotNull();
        assertThat(insufficientAi.status()).isEqualTo(AiExplanationStatus.INSUFFICIENT_DATA);
        assertThat(insufficientAi.explanation().summary()).contains("INSUFFICIENT_DATA");

        // Verify OpenAPI documents Phase 8 endpoints
        ResponseEntity<String> openApi = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(openApi.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(openApi.getBody())
                .contains("/api/v1/instruments/{symbol}/ai-explanation")
                .contains("/api/v1/instruments/{symbol}/ai-explanation/regenerate");
    }

    private HttpHeaders registerAndLogin(String email) {
        restTemplate.postForEntity(
                "/api/v1/auth/register",
                new RegisterRequest(email, "AiTestPass123", "AI Tester", "Europe/Istanbul"),
                RegisterResponse.class);
        LoginResponse login =
                restTemplate
                        .postForEntity(
                                "/api/v1/auth/login",
                                new LoginRequest(email, "AiTestPass123"),
                                LoginResponse.class)
                        .getBody();
        assertThat(login).isNotNull();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.accessToken());
        return headers;
    }
}
