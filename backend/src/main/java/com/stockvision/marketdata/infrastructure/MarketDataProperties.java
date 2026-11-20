package com.stockvision.marketdata.infrastructure;

import jakarta.annotation.PostConstruct;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties governing active market data provider selection and freshness rules.
 */
@ConfigurationProperties(prefix = "app.marketdata")
public class MarketDataProperties {

    private String provider = "mock";
    private long staleThresholdHours = 24L;
    private int defaultLookbackDays = 90;
    private Yahoo yahoo = new Yahoo();

    @PostConstruct
    public void validate() {
        if (provider == null || provider.isBlank()) {
            throw new IllegalStateException("app.marketdata.provider must not be blank");
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        if (!"mock".equals(normalized) && !"yahoo".equals(normalized)) {
            throw new IllegalStateException(
                    "Unsupported market data provider '"
                            + provider
                            + "'. Supported values: 'mock', 'yahoo'.");
        }
        if (staleThresholdHours < 1L) {
            throw new IllegalStateException("app.marketdata.stale-threshold-hours must be >= 1");
        }
        if (defaultLookbackDays < 1) {
            throw new IllegalStateException("app.marketdata.default-lookback-days must be >= 1");
        }
        if ("yahoo".equals(normalized)) {
            if (yahoo == null || yahoo.getBaseUrl() == null || yahoo.getBaseUrl().isBlank()) {
                throw new IllegalStateException("app.marketdata.yahoo.base-url must not be blank");
            }
            if (yahoo.getTimeoutMs() < 1000L) {
                throw new IllegalStateException("app.marketdata.yahoo.timeout-ms must be >= 1000");
            }
            if (yahoo.getQuoteCacheSeconds() < 0L) {
                throw new IllegalStateException(
                        "app.marketdata.yahoo.quote-cache-seconds must be >= 0");
            }
        }
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public long getStaleThresholdHours() {
        return staleThresholdHours;
    }

    public void setStaleThresholdHours(long staleThresholdHours) {
        this.staleThresholdHours = staleThresholdHours;
    }

    public int getDefaultLookbackDays() {
        return defaultLookbackDays;
    }

    public void setDefaultLookbackDays(int defaultLookbackDays) {
        this.defaultLookbackDays = defaultLookbackDays;
    }

    public Yahoo getYahoo() {
        return yahoo;
    }

    public void setYahoo(Yahoo yahoo) {
        this.yahoo = yahoo;
    }

    /**
     * Configuration properties for the public Yahoo Finance delayed BIST adapter.
     */
    public static class Yahoo {

        private String baseUrl = "https://query1.finance.yahoo.com";
        private String symbolSuffix = ".IS";
        private long timeoutMs = 10000L;
        private long quoteCacheSeconds = 60L;
        private String userAgent =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getSymbolSuffix() {
            return symbolSuffix;
        }

        public void setSymbolSuffix(String symbolSuffix) {
            this.symbolSuffix = symbolSuffix;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public long getQuoteCacheSeconds() {
            return quoteCacheSeconds;
        }

        public void setQuoteCacheSeconds(long quoteCacheSeconds) {
            this.quoteCacheSeconds = quoteCacheSeconds;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }
    }
}
