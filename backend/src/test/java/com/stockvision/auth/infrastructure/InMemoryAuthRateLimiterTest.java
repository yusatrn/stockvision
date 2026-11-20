package com.stockvision.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockvision.auth.domain.RateLimitDecision;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InMemoryAuthRateLimiterTest {

    @Test
    void enforcesWindowLimitAndResetsAfterWindowElapses() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-19T12:00:00Z"));
        RateLimitProperties properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setMaxRequests(3);
        properties.setWindowSeconds(60L);
        properties.setMaxTrackedKeys(100);

        InMemoryAuthRateLimiter limiter = new InMemoryAuthRateLimiter(properties, clock);

        assertThat(limiter.tryConsume("127.0.0.1:/api/v1/auth/login").allowed()).isTrue();
        assertThat(limiter.tryConsume("127.0.0.1:/api/v1/auth/login").allowed()).isTrue();
        assertThat(limiter.tryConsume("127.0.0.1:/api/v1/auth/login").allowed()).isTrue();

        RateLimitDecision blocked = limiter.tryConsume("127.0.0.1:/api/v1/auth/login");
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isEqualTo(60L);

        assertThat(limiter.tryConsume("10.0.0.2:/api/v1/auth/login").allowed()).isTrue();

        clock.advanceSeconds(60L);
        assertThat(limiter.tryConsume("127.0.0.1:/api/v1/auth/login").allowed()).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant initial) {
            this.current = initial;
        }

        void advanceSeconds(long seconds) {
            this.current = this.current.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
