package com.swiftdrop.parcel_pricing_service.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.swiftdrop.parcel_pricing_service.common.ApiException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;

/** IdentityClient against a local stub of identity-merchant-service: token forwarding and outage handling. */
class IdentityClientTest {
    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> body = new AtomicReference<>("{}");
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final UUID userId = UUID.randomUUID();
    private final Jwt jwt = Jwt.withTokenValue("user-token").header("alg", "RS256").subject(UUID.randomUUID().toString()).build();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            method.set(exchange.getRequestMethod());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private IdentityClient client() {
        return new IdentityClient("http://127.0.0.1:" + server.getAddress().getPort(), JsonMapper.builder().build());
    }

    private String user() {
        return """
            {"id":"%s","email":"nimal@example.com","fullName":"Nimal Perera","phoneNumber":"+94771234567","roles":["CUSTOMER"]}
            """.formatted(userId);
    }

    @Test
    void currentUserForwardsTheCallersTokenAndIgnoresUnknownFields() {
        body.set(user());
        var found = client().currentUser(jwt);
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(userId);
        assertThat(found.get().fullName()).isEqualTo("Nimal Perera");
        assertThat(authorization.get()).isEqualTo("Bearer user-token");
    }

    @Test
    void currentUserIsEmptyForMissingTokenErrorsGarbageAndOutages() {
        assertThat(client().currentUser(null)).isEmpty();
        status.set(401);
        assertThat(client().currentUser(jwt)).isEmpty();
        status.set(200);
        body.set("not json");
        assertThat(client().currentUser(jwt)).isEmpty();
        IdentityClient client = client();
        server.stop(0);
        assertThat(client.currentUser(jwt)).isEmpty();
    }

    @Test
    void currentMerchantReturnsTheBusinessContact() {
        body.set("{\"id\":\"%s\",\"businessName\":\"Lanka Books\",\"contactEmail\":\"shop@example.com\",\"contactPhone\":\"0112345678\"}"
                .formatted(userId));
        var merchant = client().currentMerchant("api-key-token");
        assertThat(merchant).isPresent();
        assertThat(merchant.get().businessName()).isEqualTo("Lanka Books");
        assertThat(authorization.get()).isEqualTo("Bearer api-key-token");
    }

    @Test
    void currentMerchantIsEmptyWithoutATokenOrWhenIdentityFails() {
        assertThat(client().currentMerchant(null)).isEmpty();
        assertThat(client().currentMerchant("  ")).isEmpty();
        status.set(503);
        assertThat(client().currentMerchant("token")).isEmpty();
        status.set(200);
        body.set("[");
        assertThat(client().currentMerchant("token")).isEmpty();
    }

    @Test
    void updateSendsOnlyTheChangedFieldsAsPatch() throws Exception {
        body.set(user());
        var updated = client().updateCurrentUser(jwt, "Nimal Perera", null);
        assertThat(updated.email()).isEqualTo("nimal@example.com");
        assertThat(method.get()).isEqualTo("PATCH");
        var sent = JsonMapper.builder().build().readTree(requestBody.get());
        assertThat(sent.path("fullName").asString()).isEqualTo("Nimal Perera");
        assertThat(sent.has("phoneNumber")).isFalse();
    }

    @Test
    void rejectedProfileChangesAreABadRequest() {
        status.set(400);
        assertThatThrownBy(() -> client().updateCurrentUser(jwt, null, "123")).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.getCode()).isEqualTo("INVALID_PROFILE");
        });
    }

    @Test
    void identityFailuresDuringUpdateAreServiceUnavailable() {
        status.set(500);
        assertUnavailable(client());
        status.set(200);
        body.set("not json");
        assertUnavailable(client());
        IdentityClient client = client();
        server.stop(0);
        assertUnavailable(client);
    }

    private void assertUnavailable(IdentityClient client) {
        assertThatThrownBy(() -> client.updateCurrentUser(jwt, "A", "B")).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(e.getCode()).isEqualTo("IDENTITY_UNAVAILABLE");
        });
    }
}
