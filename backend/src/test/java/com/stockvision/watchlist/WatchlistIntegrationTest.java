package com.stockvision.watchlist;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockvision.TestSecuritySupport;
import com.stockvision.auth.api.LoginRequest;
import com.stockvision.auth.api.LoginResponse;
import com.stockvision.auth.api.RegisterRequest;
import com.stockvision.auth.api.RegisterResponse;
import com.stockvision.common.api.ApiErrorResponse;
import com.stockvision.marketdata.domain.FreshnessStatus;
import com.stockvision.watchlist.api.AddWatchlistItemRequest;
import com.stockvision.watchlist.api.CreateWatchlistRequest;
import com.stockvision.watchlist.api.UpdateWatchlistRequest;
import com.stockvision.watchlist.api.WatchlistCollectionResponse;
import com.stockvision.watchlist.api.WatchlistItemResponse;
import com.stockvision.watchlist.api.WatchlistResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
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
class WatchlistIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        TestSecuritySupport.registerJwtSecret(registry);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void configurePatchSupport() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void managesMultipleWatchlistsPreventsDuplicatesAndEnforcesOwnership() {
        HttpHeaders userA = registerAndLogin("watchlist.a@example.com");
        HttpHeaders userB = registerAndLogin("watchlist.b@example.com");

        // Unauthenticated check
        assertThat(restTemplate.getForEntity("/api/v1/watchlists", ApiErrorResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // User A creates two lists
        ResponseEntity<WatchlistResponse> coreCreate =
                restTemplate.exchange(
                        "/api/v1/watchlists",
                        HttpMethod.POST,
                        new HttpEntity<>(new CreateWatchlistRequest("BIST Core", "Large caps"), userA),
                        WatchlistResponse.class);
        assertThat(coreCreate.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        WatchlistResponse core = coreCreate.getBody();
        assertThat(core).isNotNull();

        ResponseEntity<WatchlistResponse> bankingCreate =
                restTemplate.exchange(
                        "/api/v1/watchlists",
                        HttpMethod.POST,
                        new HttpEntity<>(new CreateWatchlistRequest("Banking", null), userA),
                        WatchlistResponse.class);
        assertThat(bankingCreate.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Duplicate list name rejected (case-insensitive)
        ResponseEntity<ApiErrorResponse> dupList =
                restTemplate.exchange(
                        "/api/v1/watchlists",
                        HttpMethod.POST,
                        new HttpEntity<>(new CreateWatchlistRequest("bist core", null), userA),
                        ApiErrorResponse.class);
        assertThat(dupList.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dupList.getBody().code()).isEqualTo("CONFLICT");

        // Add normalized ticker to watchlist
        ResponseEntity<WatchlistItemResponse> addedItem =
                restTemplate.exchange(
                        "/api/v1/watchlists/" + core.id() + "/items",
                        HttpMethod.POST,
                        new HttpEntity<>(new AddWatchlistItemRequest("thyao", "Flagship"), userA),
                        WatchlistItemResponse.class);
        assertThat(addedItem.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(addedItem.getBody().symbol()).isEqualTo("THYAO");
        assertThat(addedItem.getBody().latestQuote()).isNotNull();
        assertThat(addedItem.getBody().latestQuote().simulated()).isTrue();

        // Duplicate symbol in same watchlist rejected -> 409 CONFLICT
        ResponseEntity<ApiErrorResponse> dupItem =
                restTemplate.exchange(
                        "/api/v1/watchlists/" + core.id() + "/items",
                        HttpMethod.POST,
                        new HttpEntity<>(new AddWatchlistItemRequest("THYAO", null), userA),
                        ApiErrorResponse.class);
        assertThat(dupItem.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // User A lists watchlists with provenance metadata
        ResponseEntity<WatchlistCollectionResponse> listResponse =
                restTemplate.exchange(
                        "/api/v1/watchlists",
                        HttpMethod.GET,
                        new HttpEntity<>(userA),
                        WatchlistCollectionResponse.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody().watchlists()).hasSize(2);
        assertThat(listResponse.getBody().dataSource().simulated()).isTrue();
        assertThat(listResponse.getBody().freshness().status())
                .isEqualTo(FreshnessStatus.SIMULATED_ONLY);

        // Ownership checks: User B gets 404 attempting rename/add/remove/delete on User A's watchlist
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/watchlists/" + core.id(),
                                        HttpMethod.PATCH,
                                        new HttpEntity<>(
                                                new UpdateWatchlistRequest("Hijacked", null), userB),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/watchlists/" + core.id() + "/items",
                                        HttpMethod.POST,
                                        new HttpEntity<>(
                                                new AddWatchlistItemRequest("GARAN", null), userB),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/watchlists/"
                                                + core.id()
                                                + "/items/"
                                                + addedItem.getBody().id(),
                                        HttpMethod.DELETE,
                                        new HttpEntity<>(userB),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/watchlists/" + core.id(),
                                        HttpMethod.DELETE,
                                        new HttpEntity<>(userB),
                                        ApiErrorResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        // User B's own collection stays empty
        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/watchlists",
                                        HttpMethod.GET,
                                        new HttpEntity<>(userB),
                                        WatchlistCollectionResponse.class)
                                .getBody()
                                .watchlists())
                .isEmpty();

        // User A updates, deletes item, and deletes list cleanly
        ResponseEntity<WatchlistResponse> renamed =
                restTemplate.exchange(
                        "/api/v1/watchlists/" + core.id(),
                        HttpMethod.PATCH,
                        new HttpEntity<>(new UpdateWatchlistRequest("BIST Focus", "Updated"), userA),
                        WatchlistResponse.class);
        assertThat(renamed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(renamed.getBody().name()).isEqualTo("BIST Focus");

        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/watchlists/"
                                                + core.id()
                                                + "/items/"
                                                + addedItem.getBody().id(),
                                        HttpMethod.DELETE,
                                        new HttpEntity<>(userA),
                                        Void.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(
                        restTemplate
                                .exchange(
                                        "/api/v1/watchlists/" + core.id(),
                                        HttpMethod.DELETE,
                                        new HttpEntity<>(userA),
                                        Void.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    private HttpHeaders registerAndLogin(String email) {
        restTemplate.postForEntity(
                "/api/v1/auth/register",
                new RegisterRequest(email, "WatchlistPass123", "Watchlist Tester", "Europe/Istanbul"),
                RegisterResponse.class);
        LoginResponse login =
                restTemplate
                        .postForEntity(
                                "/api/v1/auth/login",
                                new LoginRequest(email, "WatchlistPass123"),
                                LoginResponse.class)
                        .getBody();
        assertThat(login).isNotNull();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.accessToken());
        return headers;
    }
}
