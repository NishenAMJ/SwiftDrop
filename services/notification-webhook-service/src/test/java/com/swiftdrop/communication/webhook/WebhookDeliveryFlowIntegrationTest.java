package com.swiftdrop.communication.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.sun.net.httpserver.HttpServer;
import com.swiftdrop.communication.config.RabbitConfig;
import com.swiftdrop.communication.support.AbstractIntegrationTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end webhook flow: a parcel.status-changed event goes through RabbitMQ, a delivery row
 * is created, the scheduled dispatcher POSTs it to a real HTTP server, and failures are retried.
 * The "merchant server" is the JDK's built-in HttpServer, so no extra dependency is needed.
 */
class WebhookDeliveryFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private WebhookDeliveryRepository deliveryRepository;

    @Autowired
    private WebhookSigner signer;

    @Autowired
    private JsonMapper jsonMapper;

    /** One request the fake merchant server received, and the status it answered with. */
    private record Received(int status, String signature, String event, String body) {
    }

    private HttpServer merchantServer;
    private final AtomicInteger calls = new AtomicInteger();
    private final List<Received> received = new CopyOnWriteArrayList<>();
    /** The first N calls get HTTP 500, later calls get 200. */
    private volatile int failFirst;

    @BeforeEach
    void startMerchantServer() throws IOException {
        merchantServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        merchantServer.createContext("/hook", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            int status = calls.incrementAndGet() <= failFirst ? 500 : 200;
            received.add(new Received(status,
                    exchange.getRequestHeaders().getFirst("X-SwiftDrop-Signature"),
                    exchange.getRequestHeaders().getFirst("X-SwiftDrop-Event"),
                    body));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        merchantServer.start();
    }

    @AfterEach
    void stopMerchantServer() {
        merchantServer.stop(0);
    }

    @Test
    void failedDeliveryIsRetriedAndArrivesWithValidSignature() throws Exception {
        failFirst = 1;
        UUID merchantId = UUID.randomUUID();
        String secret = createSubscription(merchantId).get("secret").asText();
        UUID eventId = UUID.randomUUID();
        UUID customerUserId = UUID.randomUUID();

        publishParcelEvent(eventId, merchantId, customerUserId);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            WebhookDelivery delivery = deliveryFor(eventId);
            assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
            assertThat(delivery.getAttemptCount()).isEqualTo(2);
            assertThat(delivery.getLastStatusCode()).isEqualTo(200);
        });

        assertThat(received).hasSize(2);
        assertThat(received.get(0).status()).isEqualTo(500);

        Received success = received.get(1);
        assertThat(success.status()).isEqualTo(200);
        assertThat(success.event()).isEqualTo("parcel.status-changed");
        // The merchant can prove the message came from SwiftDrop and was not changed.
        assertThat(signer.verify(secret, success.signature(), success.body(), 300)).isTrue();
        assertThat(signer.verify("whsec_wrong", success.signature(), success.body(), 300)).isFalse();
        // Privacy: the internal customer ID never reaches the merchant.
        assertThat(success.body()).contains(eventId.toString()).doesNotContain(customerUserId.toString())
                .doesNotContain("customerUserId");
    }

    @Test
    void deliveryGivesUpAfterAllRetriesAndCanBeRetriedManually() throws Exception {
        failFirst = Integer.MAX_VALUE;
        UUID merchantId = UUID.randomUUID();
        createSubscription(merchantId);
        UUID eventId = UUID.randomUUID();

        publishParcelEvent(eventId, merchantId, UUID.randomUUID());

        // Test config has 5 retry delays: 6 attempts in total, then FAILED.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            WebhookDelivery delivery = deliveryFor(eventId);
            assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
            assertThat(delivery.getAttemptCount()).isEqualTo(6);
            assertThat(delivery.getLastStatusCode()).isEqualTo(500);
            assertThat(delivery.getLastError()).isEqualTo("HTTP 500");
        });
        assertThat(received).hasSize(6);

        UUID deliveryId = deliveryFor(eventId).getDeliveryId();
        mockMvc.perform(post("/api/v1/webhooks/deliveries/" + deliveryId + "/retry").with(merchant(merchantId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attemptCount").value(0));

        // The dispatcher picks it up again.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(received.size()).isGreaterThan(6));
    }

    private JsonNode createSubscription(UUID merchantId) throws Exception {
        String url = "http://127.0.0.1:" + merchantServer.getAddress().getPort() + "/hook";
        String response = mockMvc.perform(post("/api/v1/webhooks/subscriptions")
                        .with(merchant(merchantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "%s", "eventTypes": ["parcel.status-changed"] }
                                """.formatted(url)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response);
    }

    private void publishParcelEvent(UUID eventId, UUID merchantId, UUID customerUserId) {
        String event = """
                {
                  "eventId": "%s",
                  "eventType": "parcel.status-changed",
                  "version": 1,
                  "occurredAt": "2026-11-20T10:00:00Z",
                  "producer": "delivery-operations-service",
                  "data": {
                    "parcelId": "%s",
                    "trackingNumber": "SWD-TEST-0001",
                    "status": "OUT_FOR_DELIVERY",
                    "previousStatus": "IN_TRANSIT",
                    "customerUserId": "%s",
                    "merchantId": "%s",
                    "changedAt": "2026-11-20T10:00:00Z"
                  }
                }
                """.formatted(eventId, UUID.randomUUID(), customerUserId, merchantId);
        rabbitTemplate.convertAndSend(RabbitConfig.DELIVERY_EXCHANGE,
                RabbitConfig.ROUTING_KEY_PARCEL_STATUS_CHANGED, event);
    }

    private WebhookDelivery deliveryFor(UUID eventId) {
        List<WebhookDelivery> deliveries = deliveryRepository.findAll().stream()
                .filter(d -> eventId.equals(d.getEventId()))
                .toList();
        assertThat(deliveries).hasSize(1);
        return deliveries.getFirst();
    }

    private static RequestPostProcessor merchant(UUID merchantId) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"))
                .jwt(token -> token.claim("merchant_id", merchantId.toString()));
    }
}
