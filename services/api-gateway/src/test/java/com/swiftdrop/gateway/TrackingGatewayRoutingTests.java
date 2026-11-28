package com.swiftdrop.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class TrackingGatewayRoutingTests {

    private static final AtomicReference<String> forwardedPath = new AtomicReference<>();
    private static final AtomicReference<String> forwardedAuthorization = new AtomicReference<>();
    private static final HttpServer upstream = startUpstream();

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ReactiveJwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("TRACKING_SERVICE_URI", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
        registry.add("IDENTITY_JWKS_URI", () -> "http://127.0.0.1/unused-jwks");
    }

    @BeforeEach
    void reset() {
        forwardedPath.set(null);
        forwardedAuthorization.set(null);
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("tracking-token")
                .header("alg", "RS256")
                .subject("tracking-user")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .claim("iss", "swiftdrop-identity")
                .claim("aud", List.of("swiftdrop-api"))
                .claim("roles", List.of("CUSTOMER"))
                .build();
        when(jwtDecoder.decode("tracking-token")).thenReturn(Mono.just(jwt));
    }

    @AfterAll
    static void stopUpstream() {
        upstream.stop(0);
    }

    @Test
    void publicTrackingAndHistoryEndpointsAreForwardedWithoutAuth() {
        webTestClient.get()
                .uri("/api/v1/public/tracking/SWD-2026-000001")
                .exchange()
                .expectStatus().isOk();
        assertThat(forwardedPath.get()).isEqualTo("/api/v1/public/tracking/SWD-2026-000001");
        assertThat(forwardedAuthorization.get()).isNull();

        webTestClient.get()
                .uri("/api/v1/public/tracking/SWD-2026-000001/history")
                .exchange()
                .expectStatus().isOk();
        assertThat(forwardedPath.get()).isEqualTo("/api/v1/public/tracking/SWD-2026-000001/history");
        assertThat(forwardedAuthorization.get()).isNull();
    }

    @Test
    void trackingAndReportsRoutesRequireValidJwtAndForwardAuthorization() {
        webTestClient.get()
                .uri("/api/v1/reports/daily")
                .exchange()
                .expectStatus().isUnauthorized();
        assertThat(forwardedPath.get()).isNull();

        webTestClient.get()
                .uri("/api/v1/reports/daily")
                .header(HttpHeaders.AUTHORIZATION, "Bearer tracking-token")
                .exchange()
                .expectStatus().isOk();
        assertThat(forwardedPath.get()).isEqualTo("/api/v1/reports/daily");
        assertThat(forwardedAuthorization.get()).isEqualTo("Bearer tracking-token");

        webTestClient.get()
                .uri("/api/v1/tracking/SWD-2026-000001")
                .header(HttpHeaders.AUTHORIZATION, "Bearer tracking-token")
                .exchange()
                .expectStatus().isOk();
        assertThat(forwardedPath.get()).isEqualTo("/api/v1/tracking/SWD-2026-000001");
    }

    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                forwardedPath.set(exchange.getRequestURI().getPath());
                forwardedAuthorization.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                exchange.getRequestBody().readAllBytes();
                byte[] body = "{\"service\":\"tracking\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start tracking stub upstream", e);
        }
    }
}
