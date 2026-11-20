package com.stockvision.auth.infrastructure;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized JWT signing and lifetime settings.
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    private static final int MIN_SECRET_BYTES = 32;
    private static final long MIN_TTL_SECONDS = 60L;
    private static final long MAX_TTL_SECONDS = 86400L;
    private static final long MIN_REFRESH_TTL_SECONDS = 300L;
    private static final long MAX_REFRESH_TTL_SECONDS = 2_592_000L;

    private String secret = "";
    private String issuer = "stockvision-api";
    private long accessTokenTtlSeconds = 900L;
    private long refreshTokenTtlSeconds = 604_800L;

    @PostConstruct
    public void validate() {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("app.security.jwt.issuer must not be blank");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "app.security.jwt.secret (JWT_SECRET) must be configured via environment variable");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be at least 32 bytes (256 bits) for HS256");
        }
        if (accessTokenTtlSeconds < MIN_TTL_SECONDS || accessTokenTtlSeconds > MAX_TTL_SECONDS) {
            throw new IllegalStateException(
                    "app.security.jwt.access-token-ttl-seconds must be between 60 and 86400");
        }
        if (refreshTokenTtlSeconds < MIN_REFRESH_TTL_SECONDS
                || refreshTokenTtlSeconds > MAX_REFRESH_TTL_SECONDS) {
            throw new IllegalStateException(
                    "app.security.jwt.refresh-token-ttl-seconds must be between 300 and 2592000");
        }
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) {
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }
}
