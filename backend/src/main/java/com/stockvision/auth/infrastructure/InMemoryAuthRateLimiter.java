package com.stockvision.auth.infrastructure;

import com.stockvision.auth.domain.AuthRateLimiter;
import com.stockvision.auth.domain.RateLimitDecision;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Thread-safe, fixed-window in-memory rate limiter suitable for single-instance protection of
 * authentication endpoints.
 */
@Component
public class InMemoryAuthRateLimiter implements AuthRateLimiter {

    private final RateLimitProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public InMemoryAuthRateLimiter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public RateLimitDecision tryConsume(String key) {
        if (!properties.isEnabled()) {
            return RateLimitDecision.allow();
        }
        long nowEpochSecond = clock.instant().getEpochSecond();
        long windowSeconds = properties.getWindowSeconds();

        if (counters.size() >= properties.getMaxTrackedKeys() && !counters.containsKey(key)) {
            counters.entrySet()
                    .removeIf(
                            entry ->
                                    (nowEpochSecond - entry.getValue().windowStartEpochSecond())
                                            >= windowSeconds);
            if (counters.size() >= properties.getMaxTrackedKeys()) {
                return RateLimitDecision.rejected(windowSeconds);
            }
        }

        WindowCounter updated =
                counters.compute(
                        key,
                        (ignored, existing) -> {
                            if (existing == null
                                    || (nowEpochSecond - existing.windowStartEpochSecond())
                                            >= windowSeconds) {
                                return new WindowCounter(nowEpochSecond, 1, true);
                            }
                            if (existing.count() < properties.getMaxRequests()) {
                                return new WindowCounter(
                                        existing.windowStartEpochSecond(),
                                        existing.count() + 1,
                                        true);
                            }
                            return new WindowCounter(
                                    existing.windowStartEpochSecond(),
                                    existing.count(),
                                    false);
                        });

        if (updated.lastAllowed()) {
            return RateLimitDecision.allow();
        }
        long elapsed = nowEpochSecond - updated.windowStartEpochSecond();
        long retryAfter = Math.max(1L, windowSeconds - elapsed);
        return RateLimitDecision.rejected(retryAfter);
    }

    private record WindowCounter(long windowStartEpochSecond, int count, boolean lastAllowed) {
    }
}
