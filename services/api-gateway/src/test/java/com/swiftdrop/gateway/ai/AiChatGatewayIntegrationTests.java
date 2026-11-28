package com.swiftdrop.gateway.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AiChatGatewayIntegrationTests {

    private static final AtomicInteger RESPONSE_STATUS = new AtomicInteger(200);
    private static final AtomicReference<String> RESPONSE_BODY = new AtomicReference<>();
    private static final AtomicReference<String> LAST_API_KEY = new AtomicReference<>();
    private static final HttpServer GEMINI_STUB = startStub();

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ReactiveJwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("swiftdrop.ai.gemini.api-key", () -> "gateway-ai-test-key");
        registry.add("swiftdrop.ai.gemini.base-url", () -> "http://127.0.0.1:" + GEMINI_STUB.getAddress().getPort());
        registry.add("IDENTITY_JWKS_URI", () -> "http://127.0.0.1/unused-jwks");
    }

    @BeforeEach
    void resetStub() {
        RESPONSE_STATUS.set(200);
        RESPONSE_BODY.set("""
                {"candidates":[{"content":{"parts":[{"text":"Use My Bookings to follow your parcel."}]}}]}
                """);
        LAST_API_KEY.set(null);
    }

    @AfterAll
    static void stopStub() {
        GEMINI_STUB.stop(0);
    }

    @Test
    void postChatIsPublicAndReturnsGeminiAnswer() {
        webTestClient.post()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/ai/chat")
                        .queryParam("message", "How do I track a parcel?")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.answer").isEqualTo("Use My Bookings to follow your parcel.");

        assertThat(LAST_API_KEY.get()).isEqualTo("gateway-ai-test-key");
    }

    @Test
    void getChatRequiresAuthentication() {
        webTestClient.get()
                .uri("/api/v1/ai/chat?message=Hello")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void postChatWithoutMessageReturnsBadRequest() {
        webTestClient.post()
                .uri("/api/v1/ai/chat")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.answer").isEqualTo("Please enter a message.");
    }

    @Test
    void postChatUsesFallbackAnswerWhenGeminiIsOverloaded() {
        RESPONSE_STATUS.set(503);
        RESPONSE_BODY.set("{}");

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/ai/chat")
                        .queryParam("message", "Book a parcel")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.answer").value(answer ->
                        assertThat(answer.toString()).contains("Book Parcel"));
    }

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                LAST_API_KEY.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
                exchange.getRequestBody().readAllBytes();
                byte[] body = RESPONSE_BODY.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(RESPONSE_STATUS.get(), body.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start Gemini stub server", e);
        }
    }
}
