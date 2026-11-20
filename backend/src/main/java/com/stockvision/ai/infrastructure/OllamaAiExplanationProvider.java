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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Free local open-source LLM adapter speaking to an Ollama server over HTTP.
 */
public class OllamaAiExplanationProvider implements AiExplanationProvider {

    public static final String PROVIDER_ID = "ollama";

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OllamaAiExplanationProvider(AiProperties properties, ObjectMapper objectMapper) {
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
        return properties.getOllama().getModel();
    }

    @Override
    public boolean isConfigured() {
        return !properties.getOllama().getBaseUrl().isBlank()
                && !properties.getOllama().getModel().isBlank();
    }

    @Override
    public AiRawGenerationResult generate(AiPromptBundle promptBundle) {
        if (!isConfigured()) {
            throw new AiProviderException(
                    AiProviderErrorType.UNCONFIGURED, "Ollama base URL or model is missing");
        }

        String baseUrl = properties.getOllama().getBaseUrl().replaceAll("/+$", "");
        String endpoint = baseUrl + "/api/chat";
        try {
            Map<String, Object> body =
                    Map.of(
                            "model",
                            properties.getOllama().getModel(),
                            "stream",
                            false,
                            "format",
                            "json",
                            "options",
                            Map.of("temperature", 0.1),
                            "messages",
                            List.of(
                                    Map.of("role", "system", "content", promptBundle.systemPrompt()),
                                    Map.of("role", "user", "content", promptBundle.userPromptJson())));

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(endpoint))
                            .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                            .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiProviderException(
                        AiProviderErrorType.UNAVAILABLE,
                        "Ollama provider returned HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("message").path("content").asText("");
            return new AiRawGenerationResult(content, PROVIDER_ID, modelName());
        } catch (HttpTimeoutException ex) {
            throw new AiProviderException(
                    AiProviderErrorType.TIMEOUT, "Ollama explanation request timed out", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(
                    AiProviderErrorType.UNAVAILABLE, "Ollama request was interrupted", ex);
        } catch (IOException | IllegalArgumentException ex) {
            throw new AiProviderException(
                    AiProviderErrorType.UNAVAILABLE, "Ollama provider is unreachable", ex);
        }
    }
}
