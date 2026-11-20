package com.stockvision.auth.application;

import com.stockvision.auth.api.LoginRequest;
import com.stockvision.auth.api.LoginResponse;
import com.stockvision.auth.api.LogoutRequest;
import com.stockvision.auth.api.RefreshTokenRequest;
import com.stockvision.auth.api.RegisterRequest;
import com.stockvision.auth.api.RegisterResponse;
import com.stockvision.auth.domain.RefreshToken;
import com.stockvision.auth.domain.RefreshTokenRepository;
import com.stockvision.auth.infrastructure.JwtProperties;
import com.stockvision.auth.infrastructure.JwtTokenService;
import com.stockvision.auth.infrastructure.JwtTokenService.IssuedToken;
import com.stockvision.auth.infrastructure.RefreshTokenHasher;
import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.user.api.UserProfileResponse;
import com.stockvision.user.domain.EmailNormalizer;
import com.stockvision.user.domain.User;
import com.stockvision.user.domain.UserRepository;
import com.stockvision.user.domain.UserRole;
import com.stockvision.user.domain.UserStatus;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service coordinating registration, password login, rotatable refresh tokens, and
 * revocation-backed logout with anti-enumeration protections.
 */
@Service
public class AuthService {

    private static final String DEFAULT_TIMEZONE = "Europe/Istanbul";
    private static final String NEUTRAL_REGISTER_MESSAGE =
            "Sağlanan bilgiler uygunsa kayıt işleminiz tamamlanmıştır. Giriş yapabilirsiniz.";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";
    private static final String INVALID_REFRESH_TOKEN_MESSAGE =
            "Invalid, expired, or revoked refresh token";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            RefreshTokenRepository refreshTokenRepository,
            JwtProperties jwtProperties,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("stockvision-dummy-timing-guard");
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmailOrThrow(request.email());
        String timezone = resolveTimezone(request.timezone());
        String displayName = normalizeDisplayName(request.displayName());

        // Hash before checking existence so response timing stays similar for existing vs new emails.
        String passwordHash = passwordEncoder.encode(request.password());

        if (!userRepository.existsByEmail(normalizedEmail)) {
            User newUser =
                    new User(
                            UUID.randomUUID(),
                            normalizedEmail,
                            displayName,
                            passwordHash,
                            UserRole.USER,
                            UserStatus.ACTIVE,
                            timezone,
                            null,
                            null);
            try {
                userRepository.save(newUser);
            } catch (DataIntegrityViolationException ignored) {
                // Concurrent duplicate registration intentionally returns the same neutral response.
            }
        }

        return new RegisterResponse("ACCEPTED", NEUTRAL_REGISTER_MESSAGE);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmailOrThrow(request.email());
        Optional<User> maybeUser = userRepository.findByEmail(normalizedEmail);

        if (maybeUser.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new AppException(ErrorCode.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
        }

        User user = maybeUser.get();
        boolean passwordMatches = passwordEncoder.matches(request.password(), user.passwordHash());
        if (!passwordMatches || !user.isActive()) {
            throw new AppException(ErrorCode.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
        }

        return issueSessionTokens(user, null);
    }

    @Transactional(noRollbackFor = AppException.class)
    public LoginResponse refresh(RefreshTokenRequest request) {
        String tokenHash;
        try {
            tokenHash = RefreshTokenHasher.sha256Hex(request.refreshToken());
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.UNAUTHORIZED, INVALID_REFRESH_TOKEN_MESSAGE, ex);
        }

        RefreshToken existing =
                refreshTokenRepository
                        .findByTokenHash(tokenHash)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.UNAUTHORIZED,
                                                INVALID_REFRESH_TOKEN_MESSAGE));

        Instant now = clock.instant();
        if (existing.isRevoked()) {
            // Token reuse detected: revoke all active sessions for the user and persist that revocation.
            refreshTokenRepository.revokeAllActiveByUserId(existing.userId(), now);
            throw new AppException(ErrorCode.UNAUTHORIZED, INVALID_REFRESH_TOKEN_MESSAGE);
        }

        if (existing.isExpired(now)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, INVALID_REFRESH_TOKEN_MESSAGE);
        }

        User user =
                userRepository
                        .findById(existing.userId())
                        .filter(User::isActive)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.UNAUTHORIZED,
                                                INVALID_REFRESH_TOKEN_MESSAGE));

        return issueSessionTokens(user, existing);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        if (request == null || request.refreshToken() == null || request.refreshToken().isBlank()) {
            return;
        }
        String tokenHash = RefreshTokenHasher.sha256Hex(request.refreshToken());
        Instant now = clock.instant();
        refreshTokenRepository
                .findByTokenHash(tokenHash)
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> refreshTokenRepository.save(token.revoke(now, null)));
    }

    private LoginResponse issueSessionTokens(User user, RefreshToken tokenToRotate) {
        Instant now = clock.instant();
        IssuedToken issuedAccess = jwtTokenService.issueAccessToken(user);

        String rawRefreshToken = RefreshTokenHasher.generateOpaqueToken();
        String refreshHash = RefreshTokenHasher.sha256Hex(rawRefreshToken);
        UUID newTokenId = UUID.randomUUID();
        Instant refreshExpiresAt = now.plusSeconds(jwtProperties.getRefreshTokenTtlSeconds());

        if (tokenToRotate != null) {
            refreshTokenRepository.save(tokenToRotate.revoke(now, newTokenId));
        }

        refreshTokenRepository.save(
                new RefreshToken(
                        newTokenId,
                        user.id(),
                        refreshHash,
                        refreshExpiresAt,
                        null,
                        null,
                        now));

        return new LoginResponse(
                issuedAccess.accessToken(),
                "Bearer",
                issuedAccess.expiresInSeconds(),
                issuedAccess.expiresAt(),
                UserProfileResponse.from(user),
                rawRefreshToken,
                refreshExpiresAt);
    }

    private static String normalizeEmailOrThrow(String rawEmail) {
        try {
            return EmailNormalizer.normalize(rawEmail);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid email address");
        }
    }

    private static String resolveTimezone(String rawTimezone) {
        if (rawTimezone == null || rawTimezone.isBlank()) {
            return DEFAULT_TIMEZONE;
        }
        String candidate = rawTimezone.trim();
        try {
            ZoneId.of(candidate);
            return candidate;
        } catch (DateTimeException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid IANA timezone identifier");
        }
    }

    private static String normalizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        return displayName.trim();
    }
}
