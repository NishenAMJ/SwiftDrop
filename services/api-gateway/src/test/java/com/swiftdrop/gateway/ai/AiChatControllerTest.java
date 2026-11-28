package com.swiftdrop.gateway.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class AiChatControllerTest {

    private static HttpServer geminiStub;
    private static String baseUrl;
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final List<RecordedRequest> REQUESTS = new ArrayList<>();
    private static final AtomicReference<Function<String, StubResponse>> HANDLER = new AtomicReference<>();

    @BeforeAll
    static void startStub() throws IOException {
        geminiStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        geminiStub.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String apiKey = exchange.getRequestHeaders().getFirst("x-goog-api-key");
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            REQUESTS.add(new RecordedRequest(exchange.getRequestMethod(), path, apiKey, contentType, body));

            StubResponse response = HANDLER.get().apply(path);
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        geminiStub.start();
        baseUrl = "http://127.0.0.1:" + geminiStub.getAddress().getPort();
    }

    @AfterAll
    static void stopStub() {
        if (geminiStub != null) {
            geminiStub.stop(0);
        }
    }

    @BeforeEach
    void resetStub() {
        REQUESTS.clear();
        HANDLER.set(path -> new StubResponse(404, "{}"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void rejectsNullOrBlankMessage(String message) {
        AiChatController controller = new AiChatController(OBJECT_MAPPER, "test-key", "gemini-2.5-flash", baseUrl);

        ResponseEntity<AiChatController.ChatResponse> response = controller.chat(message).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().answer()).isEqualTo("Please enter a message.");
        assertThat(REQUESTS).isEmpty();
    }

    @Test
    void returnsServiceUnavailableWhenApiKeyIsBlank() {
        AiChatController controller = new AiChatController(OBJECT_MAPPER, "   ", "gemini-2.5-flash", baseUrl);

        ResponseEntity<AiChatController.ChatResponse> response = controller.chat("Hello").block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().answer())
                .isEqualTo("The AI assistant is not configured. Please contact support.");
        assertThat(REQUESTS).isEmpty();
    }

    @Test
    void sendsExpectedPayloadAndJoinsMultipleNonBlankTextParts() {
        HANDLER.set(path -> new StubResponse(200, """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {"text": "   "},
                          {"text": 123},
                          {"text": "  First line.  "}
                        ]
                      }
                    },
                    {
                      "content": {
                        "parts": [
                          {"text": "Second line."}
                        ]
                      }
                    }
                  ]
                }
                """));

        AiChatController controller = new AiChatController(
                OBJECT_MAPPER, "  test-key  ", "  gemini-custom  ", baseUrl + "///");

        ResponseEntity<AiChatController.ChatResponse> response = controller.chat("Help me").block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().answer()).isEqualTo("First line.\nSecond line.");

        assertThat(REQUESTS).hasSize(1);
        RecordedRequest request = REQUESTS.getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/models/gemini-custom:generateContent");
        assertThat(request.apiKey()).isEqualTo("test-key");
        assertThat(request.contentType()).isEqualTo("application/json");

        JsonNode payload = OBJECT_MAPPER.readTree(request.body());
        assertThat(payload.path("systemInstruction").path("parts").get(0).path("text").asText())
                .contains("SwiftDrop delivery platform assistant");
        assertThat(payload.path("contents").get(0).path("role").asText()).isEqualTo("user");
        assertThat(payload.path("contents").get(0).path("parts").get(0).path("text").asText()).isEqualTo("Help me");
        assertThat(payload.path("generationConfig").path("temperature").asDouble()).isEqualTo(0.2);
    }

    @Test
    void triesFallbackCandidateModelsOn404And500And503() {
        AtomicInteger attempt = new AtomicInteger();
        HANDLER.set(path -> switch (attempt.getAndIncrement()) {
            case 0 -> new StubResponse(404, "{}");
            case 1 -> new StubResponse(500, "{}");
            case 2 -> new StubResponse(503, "{}");
            default -> new StubResponse(200, """
                    {"candidates":[{"content":{"parts":[{"text":"Recovered from fallback model."}]}}]}
                    """);
        });

        AiChatController controller = new AiChatController(OBJECT_MAPPER, "test-key", "gemini-custom", baseUrl);

        ResponseEntity<AiChatController.ChatResponse> response = controller.chat("Hello").block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().answer()).isEqualTo("Recovered from fallback model.");
        assertThat(REQUESTS).extracting(RecordedRequest::path).containsExactly(
                "/models/gemini-custom:generateContent",
                "/models/gemini-2.5-flash:generateContent",
                "/models/gemini-2.0-flash:generateContent",
                "/models/gemini-flash-latest:generateContent");
    }

    @Test
    void triesNextCandidateWhenResponseContainsNoUsableText() {
        AtomicInteger attempt = new AtomicInteger();
        HANDLER.set(path -> {
            if (attempt.getAndIncrement() == 0) {
                return new StubResponse(200, """
                        {"candidates":[{"content":{"parts":[{"text":"   "}]}}]}
                        """);
            }
            return new StubResponse(200, """
                    {"candidates":[{"content":{"parts":[{"text":"Second model answer"}]}}]}
                    """);
        });

        AiChatController controller = new AiChatController(OBJECT_MAPPER, "test-key", "gemini-2.5-flash", baseUrl);

        ResponseEntity<AiChatController.ChatResponse> response = controller.chat("Hello").block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().answer()).isEqualTo("Second model answer");
        assertThat(REQUESTS).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 500, 503})
    void returnsFallbackAnswerWhenAllCandidateModelsFailWithRetryableStatus(int status) {
        HANDLER.set(path -> new StubResponse(status, "{}"));
        AiChatController controller = new AiChatController(OBJECT_MAPPER, "test-key", "gemini-2.5-flash", baseUrl);

        ResponseEntity<AiChatController.ChatResponse> response = controller.chat("Track my parcel").block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().answer()).contains("Open My Bookings");
        assertThat(REQUESTS).hasSize(3);
    }

    @Test
    void returnsFallbackAnswerImmediatelyOn429() {
        HANDLER.set(path -> new StubResponse(429, "{}"));
        AiChatController controller = new AiChatController(OBJECT_MAPPER, "test-key", "gemini-2.5-flash", baseUrl);

        ResponseEntity<AiChatController.ChatResponse> response = controller.chat("Track my parcel").block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().answer()).contains("Open My Bookings");
        assertThat(REQUESTS).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void returnsUnauthorizedWhenGeminiRejectsCredentials(int status) {
        HANDLER.set(path -> new StubResponse(status, "{}"));
        AiChatController controller = new AiChatController(OBJECT_MAPPER, "test-key", "gemini-2.5-flash", baseUrl);

        ResponseEntity<AiChatController.ChatResponse> response = controller.chat("Hello").block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().answer())
                .isEqualTo("The AI service rejected its API configuration. Please contact support.");
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 502})
    void returnsBadGatewayForNonRetryableGeminiFailures(int status) {
        HANDLER.set(path -> new StubResponse(status, "{}"));
        AiChatController controller = new AiChatController(OBJECT_MAPPER, "test-key", "gemini-2.5-flash", baseUrl);

        ResponseEntity<AiChatController.ChatResponse> response = controller.chat("Hello").block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().answer())
                .isEqualTo("The AI assistant is temporarily unavailable. Please try again.");
    }

    @Test
    void returnsFallbackAnswerWhenGeminiIsUnreachable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        AiChatController controller = new AiChatController(
                OBJECT_MAPPER, "test-key", "gemini-2.5-flash", "http://127.0.0.1:" + closedPort);

        ResponseEntity<AiChatController.ChatResponse> response = controller.chat("Payment options?").block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().answer()).contains("secure online payment");
    }

    @Test
    void returnsFallbackAnswerWhenGeminiReturnsMalformedJson() {
        HANDLER.set(path -> new StubResponse(200, "not-valid-json"));
        AiChatController controller = new AiChatController(OBJECT_MAPPER, "test-key", "gemini-2.5-flash", baseUrl);

        ResponseEntity<AiChatController.ChatResponse> response = controller.chat("Hi").block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().answer()).startsWith("Hello!");
    }

    @ParameterizedTest
    @CsvSource({
            "How do I track my package?, Open My Bookings",
            "I want to book a shipment, Book Parcel",
            "Tell me about parcel weight limits, Book Parcel",
            "Which payment options exist?, secure online payment",
            "How can I pay?, secure online payment",
            "When will you deliver?, Delivery updates",
            "Can I work as a courier?, Delivery updates",
            "How do I become a merchant?, Account & roles",
            "Seller onboarding details, Account & roles",
            "Hello assistant, Hello! How can I help",
            "hi there, Hello! How can I help",
            "What are warehouse hours?, I can help with parcel tracking"
    })
    void fallbackAnswerMatchesSupportedKeywords(String message, String expectedFragment) {
        HANDLER.set(path -> new StubResponse(429, "{}"));
        AiChatController controller = new AiChatController(OBJECT_MAPPER, "test-key", "gemini-2.5-flash", baseUrl);

        ResponseEntity<AiChatController.ChatResponse> response = controller.chat(message).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().answer()).contains(expectedFragment);
    }

    private record RecordedRequest(String method, String path, String apiKey, String contentType, String body) {}

    private record StubResponse(int status, String body) {}
}
