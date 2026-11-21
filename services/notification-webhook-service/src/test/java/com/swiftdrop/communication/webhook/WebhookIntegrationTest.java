package com.swiftdrop.communication.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.swiftdrop.communication.event.IdentityEventListener;
import com.swiftdrop.communication.support.AbstractIntegrationTest;

import tools.jackson.databind.json.JsonMapper;

class WebhookIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebhookSubscriptionRepository subscriptionRepository;

    @Autowired
    private WebhookDeliveryRepository deliveryRepository;

    @Autowired
    private ParcelWebhookEventListener parcelWebhookEventListener;

    @Autowired
    private IdentityEventListener identityEventListener;

    @Autowired
    private JsonMapper jsonMapper;

    private RequestPostProcessor merchantJwt(UUID merchantId) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"))
                .jwt(builder -> builder.claim("merchant_id", merchantId.toString()));
    }

    private RequestPostProcessor merchantWithoutClaim() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));
    }

    private RequestPostProcessor dispatcherJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER"));
    }

    private Message createJsonMessage(String json) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
    }

    @Test
    void subscriptionLifecycleAndSecurityChecks() throws Exception {
        UUID merchantId1 = UUID.randomUUID();
        UUID merchantId2 = UUID.randomUUID();

        // 1. Missing merchant_id claim returns 403 MERCHANT_NOT_APPROVED
        mockMvc.perform(post("/api/v1/webhooks/subscriptions")
                        .with(merchantWithoutClaim())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url": "http://localhost:8086/dev/webhook-receiver",
                                  "eventTypes": ["parcel.status-changed"]
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));

        // 2. Create subscription with valid merchant_id claim -> 201 Created with secret
        String createResponse = mockMvc.perform(post("/api/v1/webhooks/subscriptions")
                        .with(merchantJwt(merchantId1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url": "http://localhost:8086/dev/webhook-receiver",
                                  "eventTypes": ["parcel.status-changed"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.secret").exists())
                .andExpect(jsonPath("$.secret").value(org.hamcrest.Matchers.startsWith("whsec_")))
                .andReturn().getResponse().getContentAsString();

        String subIdStr = jsonMapper.readTree(createResponse).get("id").asText();
        UUID subId = UUID.fromString(subIdStr);

        // 3. List subscriptions does NOT expose secret
        mockMvc.perform(get("/api/v1/webhooks/subscriptions")
                        .with(merchantJwt(merchantId1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(subIdStr))
                .andExpect(jsonPath("$.content[0].secret").doesNotExist());

        // 4. Get subscription does NOT expose secret
        mockMvc.perform(get("/api/v1/webhooks/subscriptions/" + subId)
                        .with(merchantJwt(merchantId1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(subIdStr))
                .andExpect(jsonPath("$.secret").doesNotExist());

        // 5. Merchant 2 cannot access Merchant 1's subscription -> 404 (prevents enumeration)
        mockMvc.perform(get("/api/v1/webhooks/subscriptions/" + subId)
                        .with(merchantJwt(merchantId2)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_FOUND"));

        // 6. Partial update subscription via PATCH
        mockMvc.perform(patch("/api/v1/webhooks/subscriptions/" + subId)
                        .with(merchantJwt(merchantId1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url": "http://localhost:8086/dev/webhook-receiver",
                                  "eventTypes": ["parcel.status-changed", "parcel.delivered"]
                                }
                                """))
                .andExpect(status().isOk());

        // 7. Rotate secret -> returns new secret
        mockMvc.perform(post("/api/v1/webhooks/subscriptions/" + subId + "/rotate-secret")
                        .with(merchantJwt(merchantId1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").exists());

        // 8. Delete subscription
        mockMvc.perform(delete("/api/v1/webhooks/subscriptions/" + subId)
                        .with(merchantJwt(merchantId1)))
                .andExpect(status().isNoContent());

        assertThat(subscriptionRepository.findById(subId)).isEmpty();
    }

    @Test
    void manualRetryDeliveryWorksAndEnforcesOwnershipAndFailedStatus() throws Exception {
        UUID merchantId1 = UUID.randomUUID();
        UUID merchantId2 = UUID.randomUUID();

        // Inactive on purpose: the background dispatcher then postpones the retried delivery
        // instead of sending it, so its status stays PENDING while this test checks it.
        WebhookSubscription sub1 = new WebhookSubscription(
                UUID.randomUUID(),
                merchantId1,
                "http://localhost:8086/dev/webhook-receiver",
                "parcel.status-changed",
                "dummy_secret",
                false,
                null,
                Instant.now(),
                Instant.now()
        );
        subscriptionRepository.save(sub1);

        UUID deliveryId = UUID.randomUUID();
        WebhookDelivery failedDelivery = new WebhookDelivery(
                deliveryId,
                sub1.getSubscriptionId(),
                UUID.randomUUID(),
                "parcel.status-changed",
                "{}",
                WebhookDeliveryStatus.FAILED,
                6,
                null,
                500,
                "Connection failed",
                Instant.now(),
                null
        );
        deliveryRepository.save(failedDelivery);

        // 1. Merchant 2 trying to retry Merchant 1's delivery -> 404
        mockMvc.perform(post("/api/v1/webhooks/deliveries/" + deliveryId + "/retry")
                        .with(merchantJwt(merchantId2)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DELIVERY_NOT_FOUND"));

        // 2. Merchant 1 retries FAILED delivery -> 202 Accepted
        mockMvc.perform(post("/api/v1/webhooks/deliveries/" + deliveryId + "/retry")
                        .with(merchantJwt(merchantId1)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(deliveryId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attemptCount").value(0))
                .andExpect(jsonPath("$.lastError").doesNotExist());

        WebhookDelivery updated = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(updated.getAttemptCount()).isEqualTo(0);
        assertThat(updated.getLastError()).isNull();

        // 3. Retrying a delivery that is now PENDING -> 409 Conflict with DELIVERY_NOT_RETRYABLE
        mockMvc.perform(post("/api/v1/webhooks/deliveries/" + deliveryId + "/retry")
                        .with(merchantJwt(merchantId1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELIVERY_NOT_RETRYABLE"));
    }

    @Test
    void parcelStatusChangedEnqueuesDeliveryWithoutCustomerUserId() {
        UUID merchantId = UUID.randomUUID();
        UUID customerUserId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        // Create active subscription for this merchant
        WebhookSubscription sub = new WebhookSubscription(
                UUID.randomUUID(),
                merchantId,
                "http://localhost:8086/dev/webhook-receiver",
                "parcel.status-changed",
                "dummy_encrypted_secret",
                true,
                null,
                Instant.now(),
                Instant.now()
        );
        subscriptionRepository.save(sub);

        String eventJson = """
                {
                  "eventId": "%s",
                  "eventType": "parcel.status-changed",
                  "version": 1,
                  "occurredAt": "2026-11-05T12:00:00Z",
                  "producer": "delivery-operations-service",
                  "data": {
                    "parcelId": "%s",
                    "trackingNumber": "SD-TEST-123",
                    "status": "IN_TRANSIT",
                    "previousStatus": "PICKED_UP",
                    "customerUserId": "%s",
                    "merchantId": "%s",
                    "failureReason": null,
                    "changedAt": "2026-11-05T12:00:00Z"
                  }
                }
                """.formatted(eventId, parcelId, customerUserId, merchantId);

        parcelWebhookEventListener.onMessage(createJsonMessage(eventJson));

        // Delivery should be enqueued
        List<WebhookDelivery> deliveries = deliveryRepository.findAll().stream()
                .filter(d -> d.getEventId().equals(eventId))
                .toList();
        assertThat(deliveries).hasSize(1);

        WebhookDelivery delivery = deliveries.get(0);
        // The background dispatcher may already have tried it (and failed: nothing listens on
        // that URL), so only check that it was not delivered. The real send is tested in
        // WebhookDeliveryFlowIntegrationTest.
        assertThat(delivery.getStatus()).isNotEqualTo(WebhookDeliveryStatus.DELIVERED);
        assertThat(delivery.getSubscriptionId()).isEqualTo(sub.getSubscriptionId());

        // Verify privacy: merchant payload strictly excludes customerUserId
        String payload = delivery.getPayload();
        assertThat(payload).contains("SD-TEST-123");
        assertThat(payload).contains("IN_TRANSIT");
        assertThat(payload).doesNotContain("customerUserId");
        assertThat(payload).doesNotContain(customerUserId.toString());
    }

    @Test
    void merchantSuspensionAndReactivationUpdatesSubscriptions() {
        UUID merchantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        WebhookSubscription sub = new WebhookSubscription(
                UUID.randomUUID(),
                merchantId,
                "http://localhost:8086/dev/webhook-receiver",
                "parcel.status-changed",
                "dummy_secret",
                true,
                null,
                Instant.now(),
                Instant.now()
        );
        subscriptionRepository.save(sub);

        // Suspend merchant
        UUID suspendEventId = UUID.randomUUID();
        String suspendJson = """
                {
                  "eventId": "%s",
                  "eventType": "merchant.suspended",
                  "version": 1,
                  "occurredAt": "2026-11-05T14:00:00Z",
                  "producer": "identity-merchant-service",
                  "data": {
                    "merchantId": "%s",
                    "userId": "%s",
                    "businessName": "Suspended Store",
                    "status": "SUSPENDED",
                    "reason": "Policy violation"
                  }
                }
                """.formatted(suspendEventId, merchantId, userId);

        identityEventListener.onMessage(createJsonMessage(suspendJson));

        WebhookSubscription pausedSub = subscriptionRepository.findById(sub.getSubscriptionId()).orElseThrow();
        assertThat(pausedSub.isActive()).isFalse();
        assertThat(pausedSub.getPausedReason()).isEqualTo("MERCHANT_SUSPENDED");

        // Reactivate merchant
        UUID reactivateEventId = UUID.randomUUID();
        String reactivateJson = """
                {
                  "eventId": "%s",
                  "eventType": "merchant.reactivated",
                  "version": 1,
                  "occurredAt": "2026-11-05T15:00:00Z",
                  "producer": "identity-merchant-service",
                  "data": {
                    "merchantId": "%s",
                    "userId": "%s",
                    "businessName": "Suspended Store",
                    "status": "APPROVED",
                    "reason": "Reinstated"
                  }
                }
                """.formatted(reactivateEventId, merchantId, userId);

        identityEventListener.onMessage(createJsonMessage(reactivateJson));

        WebhookSubscription resumedSub = subscriptionRepository.findById(sub.getSubscriptionId()).orElseThrow();
        assertThat(resumedSub.isActive()).isTrue();
        assertThat(resumedSub.getPausedReason()).isNull();
    }

    @Test
    void merchantReactivatedOnlyResumesSubscriptionsSuspendedBySystem() {
        UUID merchantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // Sub 1: paused due to merchant suspension
        WebhookSubscription subSuspended = new WebhookSubscription(
                UUID.randomUUID(),
                merchantId,
                "http://localhost:8086/dev/webhook-receiver",
                "parcel.status-changed",
                "dummy_secret",
                false,
                "MERCHANT_SUSPENDED",
                Instant.now(),
                Instant.now()
        );
        subscriptionRepository.save(subSuspended);

        // Sub 2: paused manually by merchant (pausedReason is NULL)
        WebhookSubscription subManualDisabled = new WebhookSubscription(
                UUID.randomUUID(),
                merchantId,
                "http://localhost:8086/dev/webhook-receiver",
                "parcel.status-changed",
                "dummy_secret",
                false,
                null,
                Instant.now(),
                Instant.now()
        );
        subscriptionRepository.save(subManualDisabled);

        // Dispatch merchant.reactivated
        UUID reactivateEventId = UUID.randomUUID();
        String reactivateJson = """
                {
                  "eventId": "%s",
                  "eventType": "merchant.reactivated",
                  "version": 1,
                  "occurredAt": "2026-11-05T15:00:00Z",
                  "producer": "identity-merchant-service",
                  "data": {
                    "merchantId": "%s",
                    "userId": "%s",
                    "businessName": "Store",
                    "status": "APPROVED",
                    "reason": "Reinstated"
                  }
                }
                """.formatted(reactivateEventId, merchantId, userId);

        identityEventListener.onMessage(createJsonMessage(reactivateJson));

        // Sub 1 was suspended by MERCHANT_SUSPENDED -> must be reactivated
        WebhookSubscription updatedSub1 = subscriptionRepository.findById(subSuspended.getSubscriptionId()).orElseThrow();
        assertThat(updatedSub1.isActive()).isTrue();
        assertThat(updatedSub1.getPausedReason()).isNull();

        // Sub 2 was disabled by merchant (pausedReason NULL) -> MUST REMAIN OFF
        WebhookSubscription updatedSub2 = subscriptionRepository.findById(subManualDisabled.getSubscriptionId()).orElseThrow();
        assertThat(updatedSub2.isActive()).isFalse();
        assertThat(updatedSub2.getPausedReason()).isNull();
    }
}
