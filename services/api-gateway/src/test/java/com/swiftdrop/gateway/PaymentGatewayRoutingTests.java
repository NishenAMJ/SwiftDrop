package com.swiftdrop.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class PaymentGatewayRoutingTests {

    private static final AtomicReference<String> forwardedPath = new AtomicReference<>();
    private static final AtomicReference<String> forwardedAuthorization = new AtomicReference<>();
    private static final AtomicReference<String> forwardedIdempotencyKey = new AtomicReference<>();
    private static final AtomicReference<String> forwardedContentType = new AtomicReference<>();
    private static final AtomicReference<String> forwardedBody = new AtomicReference<>();
    private static final HttpServer upstream = startUpstream();

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ReactiveJwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("PAYMENT_SERVICE_URI", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
        registry.add("IDENTITY_JWKS_URI", () -> "http://127.0.0.1/unused-jwks");
    }

    @BeforeEach
    void resetForwardedRequest() {
        forwardedPath.set(null);
        forwardedAuthorization.set(null);
        forwardedIdempotencyKey.set(null);
        forwardedContentType.set(null);
        forwardedBody.set(null);
    }

    @AfterAll
    static void stopUpstream() {
        upstream.stop(0);
    }

    @Test
    void paymentRoutesRequireAValidAccessToken() {
        webTestClient.get()
                .uri("/api/v1/payments/history")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void paymentRoutePreservesAuthorizationAndIdempotencyHeaders() {
        mockValidAccessToken();

        webTestClient.post()
                .uri("/api/v1/payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer gateway-test-token")
                .header("Idempotency-Key", "gateway-payment-key-001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"bookingId\":\"SWD-TEST-000001\"}")
                .exchange()
                .expectStatus().isOk();

        assertThat(forwardedPath.get()).isEqualTo("/api/v1/payments");
        assertThat(forwardedAuthorization.get()).isEqualTo("Bearer gateway-test-token");
        assertThat(forwardedIdempotencyKey.get()).isEqualTo("gateway-payment-key-001");
    }

    @Test
    void payHereCallbackIsPublicAndPreservesFormBody() {
        String form = "merchant_id=123&order_id=SWD-TEST-000001&status_code=2";

        webTestClient.post()
                .uri("/api/v1/payments/callbacks/payhere")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .exchange()
                .expectStatus().isOk();

        assertThat(forwardedPath.get()).isEqualTo("/api/v1/payments/callbacks/payhere");
        assertThat(forwardedAuthorization.get()).isNull();
        assertThat(forwardedContentType.get()).startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        assertThat(forwardedBody.get()).isEqualTo(form);
    }

    private void mockValidAccessToken() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("gateway-test-token")
                .header("alg", "RS256")
                .subject("gateway-test-user")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .claim("iss", "swiftdrop-identity")
                .claim("aud", List.of("swiftdrop-api"))
                .claim("roles", List.of("DISPATCHER"))
                .build();
        when(jwtDecoder.decode("gateway-test-token")).thenReturn(Mono.just(jwt));
    }

    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                forwardedPath.set(exchange.getRequestURI().toString());
                forwardedAuthorization.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                forwardedIdempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                forwardedContentType.set(exchange.getRequestHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
                forwardedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = "{\"forwarded\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                exchange.sendResponseHeaders(200, response.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(response);
                }
            });
            server.start();
            return server;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot start the gateway test upstream", exception);
        }
    }
}
