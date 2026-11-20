package com.stockvision.common.config;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized CORS settings. Rejects wildcard origins to enforce explicit origin allow-lists.
 */
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    private List<String> allowedOrigins = new ArrayList<>();
    private List<String> allowedMethods =
            new ArrayList<>(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    private List<String> allowedHeaders =
            new ArrayList<>(List.of("Authorization", "Content-Type", "X-Trace-Id"));
    private List<String> exposedHeaders = new ArrayList<>(List.of("X-Trace-Id"));
    private boolean allowCredentials = true;
    private long maxAgeSeconds = 3600L;

    @PostConstruct
    public void validate() {
        for (String origin : getSanitizedAllowedOrigins()) {
            if (origin.contains("*")) {
                throw new IllegalStateException(
                        "Wildcard CORS origins are forbidden; configure explicit origins.");
            }
        }
    }

    public List<String> getSanitizedAllowedOrigins() {
        if (allowedOrigins == null) {
            return List.of();
        }
        return allowedOrigins.stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .map(String::trim)
                .toList();
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins != null ? new ArrayList<>(allowedOrigins) : new ArrayList<>();
    }

    public List<String> getAllowedMethods() {
        return allowedMethods;
    }

    public void setAllowedMethods(List<String> allowedMethods) {
        this.allowedMethods = allowedMethods != null ? new ArrayList<>(allowedMethods) : new ArrayList<>();
    }

    public List<String> getAllowedHeaders() {
        return allowedHeaders;
    }

    public void setAllowedHeaders(List<String> allowedHeaders) {
        this.allowedHeaders = allowedHeaders != null ? new ArrayList<>(allowedHeaders) : new ArrayList<>();
    }

    public List<String> getExposedHeaders() {
        return exposedHeaders;
    }

    public void setExposedHeaders(List<String> exposedHeaders) {
        this.exposedHeaders = exposedHeaders != null ? new ArrayList<>(exposedHeaders) : new ArrayList<>();
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    public long getMaxAgeSeconds() {
        return maxAgeSeconds;
    }

    public void setMaxAgeSeconds(long maxAgeSeconds) {
        this.maxAgeSeconds = maxAgeSeconds;
    }
}
