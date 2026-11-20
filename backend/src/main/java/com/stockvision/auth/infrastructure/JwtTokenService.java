package com.stockvision.auth.infrastructure;

import com.stockvision.auth.domain.JwtTokenClaims;
import com.stockvision.user.domain.User;
import com.stockvision.user.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies stateless HS256 JWT access tokens using UTC timestamps.
 */
@Service
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String TOKEN_TYPE_ACCESS = "access";

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey secretKey;
    private final JwtParser jwtParser;

    public JwtTokenService(JwtProperties properties, Clock clock) {
        properties.validate();
        this.properties = properties;
        this.clock = clock;
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.jwtParser =
                Jwts.parser()
                        .verifyWith(this.secretKey)
                        .requireIssuer(properties.getIssuer())
                        .clock(() -> Date.from(this.clock.instant()))
                        .build();
    }

    public IssuedToken issueAccessToken(User user) {
        Instant issuedAt = Instant.ofEpochSecond(clock.instant().getEpochSecond());
        Instant expiresAt = issuedAt.plusSeconds(properties.getAccessTokenTtlSeconds());
        String tokenId = UUID.randomUUID().toString();

        String token =
                Jwts.builder()
                        .subject(user.id().toString())
                        .issuer(properties.getIssuer())
                        .id(tokenId)
                        .issuedAt(Date.from(issuedAt))
                        .expiration(Date.from(expiresAt))
                        .claim(CLAIM_ROLE, user.role().name())
                        .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                        .signWith(secretKey, Jwts.SIG.HS256)
                        .compact();

        return new IssuedToken(token, expiresAt, properties.getAccessTokenTtlSeconds());
    }

    public Optional<JwtTokenClaims> parseAndValidateAccessToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();
            String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
            if (!TOKEN_TYPE_ACCESS.equals(tokenType)) {
                return Optional.empty();
            }
            String subject = claims.getSubject();
            String roleClaim = claims.get(CLAIM_ROLE, String.class);
            if (subject == null || roleClaim == null || claims.getIssuedAt() == null || claims.getExpiration() == null) {
                return Optional.empty();
            }
            UUID userId = UUID.fromString(subject);
            UserRole role = UserRole.valueOf(roleClaim);
            return Optional.of(
                    new JwtTokenClaims(
                            userId,
                            role,
                            claims.getIssuedAt().toInstant(),
                            claims.getExpiration().toInstant(),
                            claims.getId()));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT access token due to {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * Newly minted access token and its metadata.
     */
    public record IssuedToken(String accessToken, Instant expiresAt, long expiresInSeconds) {
    }
}
