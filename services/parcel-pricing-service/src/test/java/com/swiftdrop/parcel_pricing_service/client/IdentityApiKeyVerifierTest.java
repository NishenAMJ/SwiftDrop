package com.swiftdrop.parcel_pricing_service.client;

import com.sun.net.httpserver.HttpServer;
import com.swiftdrop.parcel_pricing_service.exception.ApiKeyVerificationUnavailableException;
import com.swiftdrop.parcel_pricing_service.security.MerchantPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Talks to a stub Identity on a random local port. */
class IdentityApiKeyVerifierTest {

    private HttpServer identity;
    private final AtomicReference<Integer> status = new AtomicReference<>(200);
    private final AtomicReference<String> body = new AtomicReference<>("{\"accessToken\":\"merchant-jwt\",\"tokenType\":\"Bearer\"}");
    private final AtomicReference<String> receivedKey = new AtomicReference<>();
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private IdentityApiKeyVerifier verifier;

    @BeforeEach
    void start() throws IOException {
        identity = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        identity.createContext("/api/v1/auth/token", exchange -> {
            receivedKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        identity.start();
        verifier = new IdentityApiKeyVerifier("http://127.0.0.1:" + identity.getAddress().getPort(),
                JsonMapper.builder().build(), decoder);
    }

    @AfterEach
    void stop() {
        identity.stop(0);
    }

    @Test
    void validKey_isExchangedAndTheTokenVerified() {
        UUID user = UUID.randomUUID();
        UUID merchant = UUID.randomUUID();
        when(decoder.decode("merchant-jwt")).thenReturn(Jwt.withTokenValue("merchant-jwt").header("alg", "RS256")
                .subject(user.toString()).claim("roles", List.of("MERCHANT")).claim("merchant_id", merchant.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build());

        Optional<MerchantPrincipal> result = verifier.verify("sk_live_123");

        assertThat(receivedKey.get()).isEqualTo("sk_live_123");
        assertThat(result).get().extracting(MerchantPrincipal::merchantId).isEqualTo(merchant);
        assertThat(result).get().extracting(MerchantPrincipal::userId).isEqualTo(user);
    }

    @Test
    void unknownKey_orSuspendedMerchant_isNotValid() {
        status.set(401);
        assertThat(verifier.verify("bad")).isEmpty();
        status.set(403);
        assertThat(verifier.verify("suspended")).isEmpty();
    }

    @Test
    void identityError_failsClosed() {
        status.set(500);
        assertThatThrownBy(() -> verifier.verify("key")).isInstanceOf(ApiKeyVerificationUnavailableException.class);
    }

    @Test
    void identityDown_failsClosed() {
        identity.stop(0);
        assertThatThrownBy(() -> verifier.verify("key")).isInstanceOf(ApiKeyVerificationUnavailableException.class);
    }

    @Test
    void tokenThatDoesNotVerify_failsClosed() {
        when(decoder.decode("merchant-jwt")).thenThrow(new JwtException("bad signature"));
        assertThatThrownBy(() -> verifier.verify("key")).isInstanceOf(ApiKeyVerificationUnavailableException.class);
    }
}
