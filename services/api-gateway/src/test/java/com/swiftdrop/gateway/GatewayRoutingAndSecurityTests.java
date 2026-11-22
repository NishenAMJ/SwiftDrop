package com.swiftdrop.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Route matching for every service, public versus protected routes, token failures, header
 * propagation and upstream outages. Each upstream is a local stub that reports what it received.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayRoutingAndSecurityTests {

    private static final AtomicReference<String> service = new AtomicReference<>();
    private static final AtomicReference<String> path = new AtomicReference<>();
    private static final AtomicReference<String> authorization = new AtomicReference<>();
    private static final AtomicReference<String> correlation = new AtomicReference<>();
    private static final HttpServer identity = upstream("identity");
    private static final HttpServer parcel = upstream("parcel");
    private static final HttpServer delivery = upstream("delivery");
    private static final HttpServer payment = upstream("payment");
    private static final int closedPort = closedPort();

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private ReactiveJwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("IDENTITY_SERVICE_URI", () -> uri(identity));
        registry.add("PARCEL_SERVICE_URI", () -> uri(parcel));
        registry.add("DELIVERY_SERVICE_URI", () -> uri(delivery));
        registry.add("PAYMENT_SERVICE_URI", () -> uri(payment));
        // Tracking is "down": nothing listens on this port.
        registry.add("TRACKING_SERVICE_URI", () -> "http://127.0.0.1:" + closedPort);
        registry.add("IDENTITY_JWKS_URI", () -> "http://127.0.0.1/unused-jwks");
    }

    @BeforeEach
    void reset() {
        service.set(null);
        path.set(null);
        authorization.set(null);
        correlation.set(null);
        Instant now = Instant.now();
        Jwt valid = Jwt.withTokenValue("valid-token").header("alg", "RS256").subject("user-1")
                .issuedAt(now).expiresAt(now.plusSeconds(600))
                .claim("iss", "swiftdrop-identity").claim("aud", List.of("swiftdrop-api"))
                .claim("roles", List.of("CUSTOMER")).build();
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> switch (invocation.<String>getArgument(0)) {
            case "valid-token" -> Mono.just(valid);
            case "expired-token" -> Mono.error(new JwtValidationException("Jwt expired",
                    List.of(new OAuth2Error("invalid_token", "Jwt expired at 2026-01-01T00:00:00Z", null))));
            default -> Mono.error(new BadJwtException("Malformed token"));
        });
    }

    @AfterAll
    static void stop() {
        for (HttpServer server : List.of(identity, parcel, delivery, payment)) server.stop(0);
    }

    @ParameterizedTest
    @CsvSource({
            "/api/v1/users/me,identity",
            "/api/v1/merchants/me/api-keys,identity",
            "/api/v1/couriers/applications,identity",
            "/api/v1/bookings/my,parcel",
            "/api/v1/customer/profile/me,parcel",
            "/api/pricing/rules,parcel",
            "/api/zones,parcel",
            "/api/merchant/orders,parcel",
            "/api/v1/warehouses,delivery",
            "/api/v1/assignments/abc/proof,delivery",
            "/api/v1/courier-profiles/by-identity/x,delivery",
            "/api/v1/payments/history,payment"})
    void authenticatedRequestsReachTheOwningService(String requestPath, String expectedService) {
        client.get().uri(requestPath).header(HttpHeaders.AUTHORIZATION, "Bearer valid-token").exchange()
                .expectStatus().isOk();
        assertThat(service.get()).isEqualTo(expectedService);
        assertThat(path.get()).isEqualTo(requestPath);
        assertThat(authorization.get()).isEqualTo("Bearer valid-token");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh",
            "/api/v1/auth/logout", "/api/v1/auth/token"})
    void authenticationEndpointsArePublic(String requestPath) {
        client.post().uri(requestPath).contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange()
                .expectStatus().isOk();
        assertThat(service.get()).isEqualTo("identity");
    }

    @Test
    void jwksIsPublicForGetOnly() {
        client.get().uri("/.well-known/jwks.json").exchange().expectStatus().isOk();
        assertThat(service.get()).isEqualTo("identity");
        client.post().uri("/.well-known/jwks.json").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void protectedRoutesRequireAToken() {
        client.get().uri("/api/v1/bookings/my").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueMatches(HttpHeaders.WWW_AUTHENTICATE, "Bearer.*");
        assertThat(service.get()).as("request must not reach the upstream").isNull();
    }

    @Test
    void malformedTokenIsRejectedAsInvalid() {
        client.get().uri("/api/v1/bookings/my").header(HttpHeaders.AUTHORIZATION, "Bearer garbage").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueMatches(HttpHeaders.WWW_AUTHENTICATE, ".*invalid_token.*");
        assertThat(service.get()).isNull();
    }

    @Test
    void expiredTokenIsRejectedAsInvalid() {
        client.get().uri("/api/v1/payments/history").header(HttpHeaders.AUTHORIZATION, "Bearer expired-token").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueMatches(HttpHeaders.WWW_AUTHENTICATE, ".*invalid_token.*");
        assertThat(service.get()).isNull();
    }

    @Test
    void publicTrackingIsOpenOnlyForTheTwoReadEndpoints() {
        client.get().uri("/api/v1/tracking/SWD-2026-000001").exchange().expectStatus().isUnauthorized();
        client.post().uri("/api/v1/public/tracking/SWD-2026-000001").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/v1/public/tracking/SWD-2026-000001/history/extra").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void unavailableUpstreamReturnsAServerErrorWithoutLeakingInternals() {
        client.get().uri("/api/v1/public/tracking/SWD-2026-000001").exchange()
                .expectStatus().is5xxServerError()
                .expectBody(String.class).value(body -> assertThat(body == null ? "" : body)
                        .doesNotContain("127.0.0.1:" + closedPort)
                        .doesNotContain("Connection refused")
                        .doesNotContain("at org."));
    }

    @Test
    void unknownRoutesAreNotForwarded() {
        client.get().uri("/api/v1/unknown").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/v1/unknown").header(HttpHeaders.AUTHORIZATION, "Bearer valid-token").exchange()
                .expectStatus().isNotFound();
        assertThat(service.get()).isNull();
    }

    @Test
    void customHeadersAreForwardedUnchanged() {
        client.get().uri("/api/v1/warehouses").header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .header("X-Correlation-Id", "corr-123").exchange().expectStatus().isOk();
        assertThat(correlation.get()).isEqualTo("corr-123");
    }

    @Test
    void healthIsPublic() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    // ---------- stubs ----------

    private static String uri(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static HttpServer upstream(String name) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                service.set(name);
                path.set(exchange.getRequestURI().getPath());
                authorization.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                correlation.set(exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
                exchange.getRequestBody().readAllBytes();
                byte[] body = ("{\"service\":\"" + name + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) { out.write(body); }
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot start stub upstream " + name, e);
        }
    }

    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
