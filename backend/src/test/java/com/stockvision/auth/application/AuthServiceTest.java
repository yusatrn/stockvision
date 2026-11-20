package com.stockvision.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockvision.TestSecuritySupport;
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
import com.stockvision.auth.infrastructure.RefreshTokenHasher;
import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.user.domain.User;
import com.stockvision.user.domain.UserRepository;
import com.stockvision.user.domain.UserRole;
import com.stockvision.user.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-11-19T12:00:00Z");

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        passwordEncoder = new BCryptPasswordEncoder(4);
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret(TestSecuritySupport.runtimeJwtSecret());
        jwtProperties.setIssuer("stockvision-api");
        jwtProperties.setAccessTokenTtlSeconds(900L);
        jwtProperties.setRefreshTokenTtlSeconds(604_800L);
        Clock fixedClock = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);
        JwtTokenService jwtTokenService = new JwtTokenService(jwtProperties, fixedClock);
        authService =
                new AuthService(
                        userRepository,
                        passwordEncoder,
                        jwtTokenService,
                        refreshTokenRepository,
                        jwtProperties,
                        fixedClock);
    }

    @Test
    void registersNewUserWithNormalizedEmailAndBcryptHash() {
        when(userRepository.existsByEmail("analyst@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterResponse response =
                authService.register(
                        new RegisterRequest(
                                "  ANALYST@Example.COM ",
                                "StrongPass123",
                                " BIST Analyst ",
                                null));

        assertThat(response.status()).isEqualTo("ACCEPTED");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.email()).isEqualTo("analyst@example.com");
        assertThat(saved.displayName()).isEqualTo("BIST Analyst");
        assertThat(saved.passwordHash()).isNotEqualTo("StrongPass123");
        assertThat(passwordEncoder.matches("StrongPass123", saved.passwordHash())).isTrue();
        assertThat(saved.role()).isEqualTo(UserRole.USER);
        assertThat(saved.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.timezone()).isEqualTo("Europe/Istanbul");
    }

    @Test
    void duplicateRegistrationReturnsNeutralResponseWithoutSavingAgain() {
        when(userRepository.existsByEmail("analyst@example.com")).thenReturn(true);

        RegisterResponse response =
                authService.register(
                        new RegisterRequest(
                                "analyst@example.com",
                                "AnotherPass99",
                                "Duplicate",
                                "Europe/Istanbul"));

        assertThat(response.status()).isEqualTo("ACCEPTED");
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidTimezoneOnRegistration() {
        assertThatThrownBy(
                        () ->
                                authService.register(
                                        new RegisterRequest(
                                                "analyst@example.com",
                                                "StrongPass123",
                                                "Test",
                                                "Invalid/Zone")))
                .isInstanceOf(AppException.class)
                .satisfies(
                        ex ->
                                assertThat(((AppException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginReturnsTokenAndProfileWithoutPasswordHash() {
        User activeUser =
                new User(
                        UUID.randomUUID(),
                        "analyst@example.com",
                        "Analyst",
                        passwordEncoder.encode("StrongPass123"),
                        UserRole.USER,
                        UserStatus.ACTIVE,
                        "Europe/Istanbul",
                        FIXED_TIME,
                        FIXED_TIME);
        when(userRepository.findByEmail("analyst@example.com")).thenReturn(Optional.of(activeUser));

        LoginResponse response =
                authService.login(new LoginRequest("  ANALYST@example.com ", "StrongPass123"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(900L);
        assertThat(response.expiresAt()).isEqualTo(FIXED_TIME.plusSeconds(900L));
        assertThat(response.user().email()).isEqualTo("analyst@example.com");
    }

    @Test
    void loginFailsWithIdenticalUnauthorizedMessageForUnknownWrongPasswordAndSuspendedUser() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        User activeUser =
                new User(
                        UUID.randomUUID(),
                        "active@example.com",
                        "Active",
                        passwordEncoder.encode("CorrectPass123"),
                        UserRole.USER,
                        UserStatus.ACTIVE,
                        "Europe/Istanbul",
                        FIXED_TIME,
                        FIXED_TIME);
        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(activeUser));

        User suspendedUser =
                new User(
                        UUID.randomUUID(),
                        "suspended@example.com",
                        "Suspended",
                        passwordEncoder.encode("CorrectPass123"),
                        UserRole.USER,
                        UserStatus.SUSPENDED,
                        "Europe/Istanbul",
                        FIXED_TIME,
                        FIXED_TIME);
        when(userRepository.findByEmail("suspended@example.com"))
                .thenReturn(Optional.of(suspendedUser));

        assertThatThrownBy(
                        () ->
                                authService.login(
                                        new LoginRequest("missing@example.com", "AnyPass123")))
                .isInstanceOf(AppException.class)
                .hasMessage("Invalid email or password")
                .satisfies(
                        ex ->
                                assertThat(((AppException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));

        assertThatThrownBy(
                        () ->
                                authService.login(
                                        new LoginRequest("active@example.com", "WrongPass123")))
                .isInstanceOf(AppException.class)
                .hasMessage("Invalid email or password")
                .satisfies(
                        ex ->
                                assertThat(((AppException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));

        assertThatThrownBy(
                        () ->
                                authService.login(
                                        new LoginRequest(
                                                "suspended@example.com", "CorrectPass123")))
                .isInstanceOf(AppException.class)
                .hasMessage("Invalid email or password")
                .satisfies(
                        ex ->
                                assertThat(((AppException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void refreshRevokesTokenFamilyOnReuseAndLogoutRevokesActiveToken() {
        UUID userId = UUID.randomUUID();
        User activeUser =
                new User(
                        userId,
                        "analyst@example.com",
                        "Analyst",
                        "hash",
                        UserRole.USER,
                        UserStatus.ACTIVE,
                        "Europe/Istanbul",
                        FIXED_TIME,
                        FIXED_TIME);
        String raw = "opaque-refresh-sample-value";
        String hash = RefreshTokenHasher.sha256Hex(raw);
        RefreshToken revokedToken =
                new RefreshToken(
                        UUID.randomUUID(),
                        userId,
                        hash,
                        FIXED_TIME.plusSeconds(3600),
                        FIXED_TIME.minusSeconds(60),
                        UUID.randomUUID(),
                        FIXED_TIME.minusSeconds(120));
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(revokedToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(raw)))
                .isInstanceOf(AppException.class)
                .satisfies(
                        ex ->
                                assertThat(((AppException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(refreshTokenRepository).revokeAllActiveByUserId(userId, FIXED_TIME);

        String logoutRaw = "logout-opaque-token";
        String logoutHash = RefreshTokenHasher.sha256Hex(logoutRaw);
        RefreshToken activeToken =
                new RefreshToken(
                        UUID.randomUUID(),
                        userId,
                        logoutHash,
                        FIXED_TIME.plusSeconds(3600),
                        null,
                        null,
                        FIXED_TIME.minusSeconds(10));
        when(refreshTokenRepository.findByTokenHash(logoutHash)).thenReturn(Optional.of(activeToken));

        authService.logout(new LogoutRequest(logoutRaw));

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().isRevoked()).isTrue();
    }
}
