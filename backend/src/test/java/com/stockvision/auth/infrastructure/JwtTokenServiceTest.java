package com.stockvision.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockvision.TestSecuritySupport;
import com.stockvision.auth.domain.JwtTokenClaims;
import com.stockvision.auth.infrastructure.JwtTokenService.IssuedToken;
import com.stockvision.user.domain.User;
import com.stockvision.user.domain.UserRole;
import com.stockvision.user.domain.UserStatus;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-11-19T12:00:00Z");

    @Test
    void issuesAndValidatesAccessTokenClaimsDeterministically() {
        MutableClock clock = new MutableClock(BASE_TIME);
        JwtProperties properties = buildProperties(TestSecuritySupport.runtimeJwtSecret(), 900L);
        JwtTokenService service = new JwtTokenService(properties, clock);

        UUID userId = UUID.randomUUID();
        User user =
                new User(
                        userId,
                        "trader@example.com",
                        "Trader",
                        "bcrypt-hash",
                        UserRole.USER,
                        UserStatus.ACTIVE,
                        "Europe/Istanbul",
                        BASE_TIME,
                        BASE_TIME);

        IssuedToken issued = service.issueAccessToken(user);
        Optional<JwtTokenClaims> parsed = service.parseAndValidateAccessToken(issued.accessToken());

        assertThat(parsed).isPresent();
        assertThat(parsed.get().userId()).isEqualTo(userId);
        assertThat(parsed.get().role()).isEqualTo(UserRole.USER);
        assertThat(parsed.get().issuedAt()).isEqualTo(BASE_TIME);
        assertThat(parsed.get().expiresAt()).isEqualTo(BASE_TIME.plusSeconds(900L));
        assertThat(parsed.get().tokenId()).isNotBlank();
    }

    @Test
    void rejectsExpiredTamperedAndWrongSecretTokens() {
        MutableClock clock = new MutableClock(BASE_TIME);
        JwtProperties properties = buildProperties(TestSecuritySupport.runtimeJwtSecret(), 120L);
        JwtTokenService service = new JwtTokenService(properties, clock);

        User user =
                new User(
                        UUID.randomUUID(),
                        "trader@example.com",
                        "Trader",
                        "bcrypt-hash",
                        UserRole.USER,
                        UserStatus.ACTIVE,
                        "Europe/Istanbul",
                        BASE_TIME,
                        BASE_TIME);

        String token = service.issueAccessToken(user).accessToken();

        int tamperIndex = token.length() - 10;
        char replacement = token.charAt(tamperIndex) == 'A' ? 'B' : 'A';
        String tampered =
                token.substring(0, tamperIndex) + replacement + token.substring(tamperIndex + 1);
        assertThat(service.parseAndValidateAccessToken(tampered)).isEmpty();

        byte[] otherKey = new byte[32];
        new SecureRandom().nextBytes(otherKey);
        JwtTokenService otherSecretService =
                new JwtTokenService(
                        buildProperties(Base64.getEncoder().encodeToString(otherKey), 120L), clock);
        assertThat(otherSecretService.parseAndValidateAccessToken(token)).isEmpty();

        clock.advanceSeconds(121L);
        assertThat(service.parseAndValidateAccessToken(token)).isEmpty();
    }

    @Test
    void rejectsShortSecretInJwtPropertiesValidation() {
        JwtProperties properties = buildProperties("short-key", 900L);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    private static JwtProperties buildProperties(String secret, long ttlSeconds) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setIssuer("stockvision-api");
        properties.setAccessTokenTtlSeconds(ttlSeconds);
        return properties;
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
