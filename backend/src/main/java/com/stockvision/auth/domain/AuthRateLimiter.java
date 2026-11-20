package com.stockvision.auth.domain;

/**
 * Rate limiter port protecting authentication endpoints against brute-force or abuse bursts.
 */
public interface AuthRateLimiter {

    RateLimitDecision tryConsume(String key);
}
