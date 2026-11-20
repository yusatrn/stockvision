package com.stockvision.ai.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for optional free AI explanation providers and deterministic fallback.
 * All credentials come strictly from environment variables and must never be committed.
 */
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private String provider = "template";
    private long timeoutMs = 15_000L;
    private Ollama ollama = new Ollama();
    private GeminiFree geminiFree = new GeminiFree();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider != null ? provider.trim() : "template";
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = Math.max(500L, timeoutMs);
    }

    public Ollama getOllama() {
        return ollama;
    }

    public void setOllama(Ollama ollama) {
        this.ollama = ollama != null ? ollama : new Ollama();
    }

    public GeminiFree getGeminiFree() {
        return geminiFree;
    }

    public void setGeminiFree(GeminiFree geminiFree) {
        this.geminiFree = geminiFree != null ? geminiFree : new GeminiFree();
    }

    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        private String model = "llama3.2:3b";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl != null ? baseUrl.trim() : "";
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model != null ? model.trim() : "";
        }
    }

    public static class GeminiFree {
        private String baseUrl = "https://generativelanguage.googleapis.com";
        private String model = "gemini-2.0-flash-lite";
        private String apiKey = "";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl != null ? baseUrl.trim() : "";
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model != null ? model.trim() : "";
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey != null ? apiKey.trim() : "";
        }
    }
}
