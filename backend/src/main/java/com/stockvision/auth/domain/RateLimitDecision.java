package com.stockvision.auth.domain;

/**
 * Result of evaluating an authentication rate-limit bucket.
 */
public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {

    public static RateLimitDecision allow() {
        return new RateLimitDecision(true, 0L);
    }

    public static RateLimitDecision rejected(long retryAfterSeconds) {
        return new RateLimitDecision(false, Math.max(1L, retryAfterSeconds));
    }
}
