package com.stockvision.auth.infrastructure;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for authentication endpoint rate limiting.
 */
@ConfigurationProperties(prefix = "app.security.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int maxRequests = 10;
    private long windowSeconds = 60L;
    private int maxTrackedKeys = 10_000;

    @PostConstruct
    public void validate() {
        if (maxRequests < 1) {
            throw new IllegalStateException("app.security.rate-limit.max-requests must be >= 1");
        }
        if (windowSeconds < 1) {
            throw new IllegalStateException(
                    "app.security.rate-limit.window-seconds must be >= 1");
        }
        if (maxTrackedKeys < 100) {
            throw new IllegalStateException(
                    "app.security.rate-limit.max-tracked-keys must be >= 100");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(long windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public int getMaxTrackedKeys() {
        return maxTrackedKeys;
    }

    public void setMaxTrackedKeys(int maxTrackedKeys) {
        this.maxTrackedKeys = maxTrackedKeys;
    }
}
