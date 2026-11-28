package com.swiftdrop.gateway.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/ai")
public class AiChatController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiChatController.class);

    private static final String SYSTEM_PROMPT = """
            You are the SwiftDrop delivery platform assistant.
            Answer clearly and helpfully about parcel tracking, bookings, payments,
            delivery information, courier work, warehouse operations, and merchant applications.
            Use only features described by the user's SwiftDrop context. Never claim to know
            private account data, tracking status, or booking details that were not provided.
            """;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public AiChatController(
            ObjectMapper objectMapper,
            @Value("${swiftdrop.ai.gemini.api-key:}") String apiKey,
            @Value("${swiftdrop.ai.gemini.model:gemini-2.5-flash}") String model,
            @Value("${swiftdrop.ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey.trim();
        this.model = model.trim();
        this.baseUrl = baseUrl.trim().replaceAll("/+$", "");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @PostMapping("/chat")
    public Mono<ResponseEntity<ChatResponse>> chat(
            @RequestParam(name = "message", required = false) String message) {
        if (message == null || message.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(new ChatResponse("Please enter a message.")));
        }
        if (apiKey.isBlank()) {
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ChatResponse("The AI assistant is not configured. Please contact support.")));
        }

        return Mono.fromCallable(() -> callGemini(message))
                .subscribeOn(Schedulers.boundedElastic())
                .map(answer -> ResponseEntity.ok(new ChatResponse(answer)))
                .onErrorResume(error -> {
                    if (error instanceof GeminiException geminiError) {
                        LOGGER.warn("Gemini request failed with HTTP {}", geminiError.statusCode());
                        if (geminiError.statusCode() == 404
                                || geminiError.statusCode() == 429
                                || geminiError.statusCode() == 500
                                || geminiError.statusCode() == 503) {
                            return Mono.just(ResponseEntity.ok(new ChatResponse(fallbackAnswer(message))));
                        }
                        return Mono.just(ResponseEntity.status(geminiStatus(geminiError.statusCode()))
                                .body(new ChatResponse(geminiMessage(geminiError.statusCode()))));
                    }
                    LOGGER.warn("Gemini request failed: {}", error.getMessage());
                    return Mono.just(ResponseEntity.ok(new ChatResponse(fallbackAnswer(message))));
                });
    }

    private HttpStatus geminiStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (statusCode == 429) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (statusCode == 503) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.BAD_GATEWAY;
    }

    private String geminiMessage(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return "The AI service rejected its API configuration. Please contact support.";
        }
        if (statusCode == 429) {
            return "The AI assistant is busy right now. Please try again in a moment.";
        }
        if (statusCode == 503) {
            return "The AI assistant is temporarily overloaded. Please try again in a moment.";
        }
        if (statusCode == 404) {
            return "The configured AI model is unavailable. Please contact support.";
        }
        return "The AI assistant is temporarily unavailable. Please try again.";
    }

    private String fallbackAnswer(String message) {
        String normalized = message.toLowerCase();
        if (normalized.contains("track")) {
            return "Open My Bookings and select the booking you want to follow. "
                    + "You can also use the tracking reference from your receipt.";
        }
        if (normalized.contains("book") || normalized.contains("parcel")) {
            return "You can book a parcel from the Book Parcel page. Enter the pickup and delivery details, "
                    + "parcel weight, and preferred delivery urgency before confirming.";
        }
        if (normalized.contains("payment") || normalized.contains("pay")) {
            return "SwiftDrop provides secure online payment options at checkout. "
                    + "Your payment status is shown on the booking receipt.";
        }
        if (normalized.contains("deliver") || normalized.contains("courier")) {
            return "Delivery updates appear in your booking history as the parcel moves through pickup, "
                    + "transit, and delivery.";
        }
        if (normalized.contains("merchant") || normalized.contains("seller")) {
            return "To apply as a merchant, open Account & roles and choose the merchant application option.";
        }
        if (normalized.contains("hello") || normalized.contains("hi")) {
            return "Hello! How can I help with your SwiftDrop delivery today?";
        }
        return "I can help with parcel tracking, bookings, payments, delivery information, courier work, "
                + "warehouse operations, and merchant applications.";
    }

    private String callGemini(String message) throws Exception {
        var payload = objectMapper.createObjectNode();
        payload.set("systemInstruction", objectMapper.createObjectNode()
                .set("parts", objectMapper.createArrayNode().add(
                        objectMapper.createObjectNode().put("text", SYSTEM_PROMPT))));

        var contents = payload.putArray("contents");
        var content = contents.addObject().put("role", "user");
        content.putArray("parts").addObject().put("text", message);
        payload.putObject("generationConfig").put("temperature", 0.2);

        int lastStatus = 404;
        for (String candidate : candidateModels()) {
            var uri = URI.create(baseUrl + "/models/" + candidate + ":generateContent");
            var request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                continue;
            }
            if (response.statusCode() == 500 || response.statusCode() == 503) {
                lastStatus = response.statusCode();
                continue;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GeminiException(response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            StringBuilder answer = new StringBuilder();
            for (JsonNode candidateNode : root.path("candidates")) {
                for (JsonNode part : candidateNode.path("content").path("parts")) {
                    JsonNode text = part.path("text");
                    if (text.isTextual() && !text.asText().isBlank()) {
                        if (!answer.isEmpty()) {
                            answer.append('\n');
                        }
                        answer.append(text.asText().trim());
                    }
                }
            }
            if (!answer.isEmpty()) {
                return answer.toString();
            }
        }

        throw new GeminiException(lastStatus);
    }

    private java.util.List<String> candidateModels() {
        var candidates = new java.util.ArrayList<String>();
        candidates.add(model);
        candidates.add("gemini-2.5-flash");
        candidates.add("gemini-2.0-flash");
        candidates.add("gemini-flash-latest");
        return candidates.stream().distinct().toList();
    }

    private static final class GeminiException extends IllegalStateException {
        private final int statusCode;

        private GeminiException(int statusCode) {
            super("Gemini returned HTTP " + statusCode);
            this.statusCode = statusCode;
        }

        private int statusCode() {
            return statusCode;
        }
    }

    public record ChatResponse(String answer) {}
}
