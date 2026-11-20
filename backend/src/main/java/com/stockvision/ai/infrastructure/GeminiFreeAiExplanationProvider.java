package com.stockvision.ai.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockvision.ai.domain.AiExplanationProvider;
import com.stockvision.ai.domain.AiPromptBundle;
import com.stockvision.ai.domain.AiProviderErrorType;
import com.stockvision.ai.domain.AiProviderException;
import com.stockvision.ai.domain.AiRawGenerationResult;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Optional free-tier Google Gemini adapter activated only when explicitly configured with a
 * user-supplied {@code AI_GEMINI_API_KEY} environment variable. Credentials are sent solely via
 * request header and never placed in URLs or logs.
 */
public class GeminiFreeAiExplanationProvider implements AiExplanationProvider {

    public static final String PROVIDER_ID = "gemini-free";

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiFreeAiExplanationProvider(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                        .build();
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public String modelName() {
        return properties.getGeminiFree().getModel();
    }

    @Override
    public boolean isConfigured() {
        return !properties.getGeminiFree().getApiKey().isBlank()
                && !properties.getGeminiFree().getBaseUrl().isBlank()
                && !properties.getGeminiFree().getModel().isBlank();
    }

    @Override
    public AiRawGenerationResult generate(AiPromptBundle promptBundle) {
        if (!isConfigured()) {
            throw new AiProviderException(
                    AiProviderErrorType.UNCONFIGURED,
                    "Gemini free-tier API key or model configuration is missing");
        }

        String baseUrl = properties.getGeminiFree().getBaseUrl().replaceAll("/+$", "");
        String encodedModel =
                URLEncoder.encode(properties.getGeminiFree().getModel(), StandardCharsets.UTF_8);
        String endpoint = baseUrl + "/v1beta/models/" + encodedModel + ":generateContent";

        try {
            Map<String, Object> body =
                    Map.of(
                            "systemInstruction",
                            Map.of(
                                    "parts",
                                    List.of(Map.of("text", promptBundle.systemPrompt()))),
                            "contents",
                            List.of(
                                    Map.of(
                                            "role",
                                            "user",
                                            "parts",
                                            List.of(Map.of("text", promptBundle.userPromptJson())))),
                            "generationConfig",
                            Map.of(
                                    "temperature",
                                    0.1,
                                    "responseMimeType",
                                    "application/json"));

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(endpoint))
                            .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                            .header("Content-Type", "application/json")
                            .header("x-goog-api-key", properties.getGeminiFree().getApiKey())
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            objectMapper.writeValueAsString(body)))
                            .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiProviderException(
                        AiProviderErrorType.UNAVAILABLE,
                        "Gemini free-tier API returned HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String text =
                    root.path("candidates")
                            .path(0)
                            .path("content")
                            .path("parts")
                            .path(0)
                            .path("text")
                            .asText("");
            return new AiRawGenerationResult(text, PROVIDER_ID, modelName());
        } catch (HttpTimeoutException ex) {
            throw new AiProviderException(
                    AiProviderErrorType.TIMEOUT, "Gemini explanation request timed out", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(
                    AiProviderErrorType.UNAVAILABLE, "Gemini request was interrupted", ex);
        } catch (IOException | IllegalArgumentException ex) {
            throw new AiProviderException(
                    AiProviderErrorType.UNAVAILABLE, "Gemini provider is unreachable", ex);
        }
    }
}
