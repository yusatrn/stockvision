package com.stockvision.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockvision.TestSecuritySupport;
import com.stockvision.auth.api.LoginRequest;
import com.stockvision.auth.api.LoginResponse;
import com.stockvision.auth.api.RegisterRequest;
import com.stockvision.auth.api.RegisterResponse;
import com.stockvision.common.api.ApiErrorResponse;
import com.stockvision.user.api.UserProfileResponse;
import com.stockvision.user.domain.UserRole;
import com.stockvision.user.domain.UserStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.security.rate-limit.max-requests=50")
@ActiveProfiles("test")
@Testcontainers
class AuthIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        TestSecuritySupport.registerJwtSecret(registry);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerLoginAndFetchMeFlowPreventsEnumerationAndNeverExposesPasswordHash()
            throws Exception {
        RegisterRequest firstRegister =
                new RegisterRequest(
                        "User.One@Example.com",
                        "SafePassword123",
                        "User One",
                        "Europe/Istanbul");
        ResponseEntity<String> firstRegisterRaw =
                restTemplate.postForEntity("/api/v1/auth/register", firstRegister, String.class);

        assertThat(firstRegisterRaw.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(firstRegisterRaw.getBody()).doesNotContain("passwordHash", "SafePassword123");
        RegisterResponse firstBody =
                objectMapper.readValue(firstRegisterRaw.getBody(), RegisterResponse.class);
        assertThat(firstBody.status()).isEqualTo("ACCEPTED");

        String storedHash =
                jdbcTemplate.queryForObject(
                        "SELECT password_hash FROM users WHERE email = ?",
                        String.class,
                        "user.one@example.com");
        assertThat(storedHash).startsWith("$2").isNotEqualTo("SafePassword123");

        RegisterRequest duplicateRegister =
                new RegisterRequest(
                        "  USER.ONE@example.com  ",
                        "AnotherPassword456",
                        "Other Name",
                        "UTC");
        ResponseEntity<RegisterResponse> duplicateResponse =
                restTemplate.postForEntity(
                        "/api/v1/auth/register", duplicateRegister, RegisterResponse.class);

        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(duplicateResponse.getBody()).isEqualTo(firstBody);

        Integer rowCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE email = ?",
                        Integer.class,
                        "user.one@example.com");
        assertThat(rowCount).isEqualTo(1);

        ResponseEntity<String> loginRaw =
                restTemplate.postForEntity(
                        "/api/v1/auth/login",
                        new LoginRequest("  user.one@EXAMPLE.com ", "SafePassword123"),
                        String.class);

        assertThat(loginRaw.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginRaw.getBody())
                .doesNotContain("passwordHash", "SafePassword123", storedHash);
        LoginResponse loginResponse =
                objectMapper.readValue(loginRaw.getBody(), LoginResponse.class);
        assertThat(loginResponse.tokenType()).isEqualTo("Bearer");
        assertThat(loginResponse.accessToken()).isNotBlank();
        assertThat(loginResponse.expiresInSeconds()).isEqualTo(900L);
        assertThat(loginResponse.user().email()).isEqualTo("user.one@example.com");
        assertThat(loginResponse.user().role()).isEqualTo(UserRole.USER);
        assertThat(loginResponse.user().status()).isEqualTo(UserStatus.ACTIVE);

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(loginResponse.accessToken());
        ResponseEntity<String> meRaw =
                restTemplate.exchange(
                        "/api/v1/users/me",
                        HttpMethod.GET,
                        new HttpEntity<>(authHeaders),
                        String.class);

        assertThat(meRaw.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meRaw.getBody()).doesNotContain("passwordHash", "SafePassword123", storedHash);
        UserProfileResponse meResponse =
                objectMapper.readValue(meRaw.getBody(), UserProfileResponse.class);
        assertThat(meResponse.id()).isEqualTo(loginResponse.user().id());
        assertThat(meResponse.email()).isEqualTo("user.one@example.com");
    }

    @Test
    void rejectsUnauthenticatedMalformedTamperedExpiredAndSuspendedTokens() {
        ResponseEntity<ApiErrorResponse> missingTokenResponse =
                restTemplate.getForEntity("/api/v1/users/me", ApiErrorResponse.class);
        assertThat(missingTokenResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(missingTokenResponse.getBody()).isNotNull();
        assertThat(missingTokenResponse.getBody().code()).isEqualTo("UNAUTHORIZED");

        HttpHeaders malformedHeaders = new HttpHeaders();
        malformedHeaders.setBearerAuth("not.a.valid.jwt");
        ResponseEntity<ApiErrorResponse> malformedResponse =
                restTemplate.exchange(
                        "/api/v1/users/me",
                        HttpMethod.GET,
                        new HttpEntity<>(malformedHeaders),
                        ApiErrorResponse.class);
        assertThat(malformedResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(malformedResponse.getBody().code()).isEqualTo("UNAUTHORIZED");

        restTemplate.postForEntity(
                "/api/v1/auth/register",
                new RegisterRequest(
                        "suspend.check@example.com",
                        "SuspendPass123",
                        "Suspend Check",
                        "Europe/Istanbul"),
                RegisterResponse.class);
        LoginResponse login =
                restTemplate
                        .postForEntity(
                                "/api/v1/auth/login",
                                new LoginRequest("suspend.check@example.com", "SuspendPass123"),
                                LoginResponse.class)
                        .getBody();
        assertThat(login).isNotNull();

        String token = login.accessToken();
        int tamperIndex = token.length() - 10;
        char replacement = token.charAt(tamperIndex) == 'A' ? 'B' : 'A';
        String tamperedToken =
                token.substring(0, tamperIndex) + replacement + token.substring(tamperIndex + 1);
        HttpHeaders tamperedHeaders = new HttpHeaders();
        tamperedHeaders.setBearerAuth(tamperedToken);
        ResponseEntity<ApiErrorResponse> tamperedResponse =
                restTemplate.exchange(
                        "/api/v1/users/me",
                        HttpMethod.GET,
                        new HttpEntity<>(tamperedHeaders),
                        ApiErrorResponse.class);
        assertThat(tamperedResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String expiredToken = buildExpiredToken(login.user().id());
        HttpHeaders expiredHeaders = new HttpHeaders();
        expiredHeaders.setBearerAuth(expiredToken);
        ResponseEntity<ApiErrorResponse> expiredResponse =
                restTemplate.exchange(
                        "/api/v1/users/me",
                        HttpMethod.GET,
                        new HttpEntity<>(expiredHeaders),
                        ApiErrorResponse.class);
        assertThat(expiredResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders validHeaders = new HttpHeaders();
        validHeaders.setBearerAuth(token);
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/users/me",
                                        HttpMethod.GET,
                                        new HttpEntity<>(validHeaders),
                                        UserProfileResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        jdbcTemplate.update(
                "UPDATE users SET status = 'SUSPENDED' WHERE email = ?",
                "suspend.check@example.com");

        ResponseEntity<ApiErrorResponse> suspendedResponse =
                restTemplate.exchange(
                        "/api/v1/users/me",
                        HttpMethod.GET,
                        new HttpEntity<>(validHeaders),
                        ApiErrorResponse.class);
        assertThat(suspendedResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(suspendedResponse.getBody().code()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void loginReturnsIdenticalUnauthorizedResponseForWrongPasswordAndMissingEmail() {
        restTemplate.postForEntity(
                "/api/v1/auth/register",
                new RegisterRequest(
                        "valid.login@example.com",
                        "CorrectPass123",
                        "Valid Login",
                        "Europe/Istanbul"),
                RegisterResponse.class);

        ResponseEntity<ApiErrorResponse> wrongPassword =
                restTemplate.postForEntity(
                        "/api/v1/auth/login",
                        new LoginRequest("valid.login@example.com", "WrongPass999"),
                        ApiErrorResponse.class);
        ResponseEntity<ApiErrorResponse> unknownEmail =
                restTemplate.postForEntity(
                        "/api/v1/auth/login",
                        new LoginRequest("nobody@example.com", "WrongPass999"),
                        ApiErrorResponse.class);

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getBody()).isNotNull();
        assertThat(unknownEmail.getBody()).isNotNull();
        assertThat(wrongPassword.getBody().code()).isEqualTo("UNAUTHORIZED");
        assertThat(unknownEmail.getBody().code()).isEqualTo("UNAUTHORIZED");
        assertThat(wrongPassword.getBody().message()).isEqualTo(unknownEmail.getBody().message());
    }

    @Test
    void registerRejectsInvalidDtoInputsWithValidationErrors() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request =
                new HttpEntity<>(
                        """
                        {"email": "not-an-email", "password": "short"}
                        """,
                        headers);

        ResponseEntity<ApiErrorResponse> response =
                restTemplate.postForEntity("/api/v1/auth/register", request, ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().fieldErrors())
                .extracting(ApiErrorResponse.FieldErrorDetail::field)
                .contains("email", "password");
    }

    @Test
    void openApiDocumentsAuthAndUserEndpoints() {
        ResponseEntity<String> openApiResponse =
                restTemplate.getForEntity("/v3/api-docs", String.class);

        assertThat(openApiResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(openApiResponse.getBody())
                .contains("/api/v1/auth/register")
                .contains("/api/v1/auth/login")
                .contains("/api/v1/users/me")
                .contains("bearerAuth");
    }

    private static String buildExpiredToken(UUID userId) {
        Instant issuedAt = Instant.now().minusSeconds(600);
        Instant expiredAt = Instant.now().minusSeconds(60);
        return Jwts.builder()
                .subject(userId.toString())
                .issuer("stockvision-api")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiredAt))
                .claim("role", UserRole.USER.name())
                .claim("token_type", "access")
                .signWith(
                        Keys.hmacShaKeyFor(
                                TestSecuritySupport.runtimeJwtSecret()
                                        .getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();
    }
}
