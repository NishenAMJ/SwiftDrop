package com.swiftdrop.communication.webhook;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.communication.common.ApiException;
import com.swiftdrop.communication.common.Problems;
import com.swiftdrop.communication.webhook.dto.CreateSubscriptionRequest;
import com.swiftdrop.communication.webhook.dto.UpdateSubscriptionRequest;
import com.swiftdrop.communication.webhook.dto.WebhookDeliveryResponse;
import com.swiftdrop.communication.webhook.dto.WebhookSubscriptionResponse;

import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional
public class WebhookSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(WebhookSubscriptionService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final UrlSafetyValidator urlSafetyValidator;
    private final WebhookSecretCrypto crypto;
    private final Clock clock;
    private final JsonMapper jsonMapper;

    public WebhookSubscriptionService(WebhookSubscriptionRepository subscriptionRepository,
                                      WebhookDeliveryRepository deliveryRepository,
                                      UrlSafetyValidator urlSafetyValidator,
                                      WebhookSecretCrypto crypto,
                                      Clock clock,
                                      JsonMapper jsonMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
        this.urlSafetyValidator = urlSafetyValidator;
        this.crypto = crypto;
        this.clock = clock;
        this.jsonMapper = jsonMapper;
    }

    public WebhookSubscriptionResponse createSubscription(UUID merchantId, CreateSubscriptionRequest request) {
        urlSafetyValidator.validate(request.getUrl());

        if (request.getEventTypes() == null || request.getEventTypes().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    Problems.badRequest("INVALID_EVENT_TYPES", "At least one event type is required"));
        }

        byte[] secretBytes = new byte[32];
        RANDOM.nextBytes(secretBytes);
        String rawSecret = "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        String encryptedSecret = crypto.encrypt(rawSecret);

        Instant now = clock.instant();
        UUID subscriptionId = UUID.randomUUID();
        String joinedEvents = String.join(",", request.getEventTypes());

        WebhookSubscription subscription = new WebhookSubscription(
                subscriptionId,
                merchantId,
                request.getUrl().trim(),
                joinedEvents,
                encryptedSecret,
                true,
                null,
                now,
                now
        );

        WebhookSubscription saved = subscriptionRepository.save(subscription);
        log.info("Created webhook subscription id={} for merchantId={}", saved.getSubscriptionId(), merchantId);
        return WebhookSubscriptionResponse.fromEntity(saved, rawSecret);
    }

    @Transactional(readOnly = true)
    public Page<WebhookSubscriptionResponse> listSubscriptions(UUID merchantId, Pageable pageable) {
        return subscriptionRepository.findByMerchantId(merchantId, pageable)
                .map(sub -> WebhookSubscriptionResponse.fromEntity(sub, null));
    }

    @Transactional(readOnly = true)
    public WebhookSubscriptionResponse getSubscription(UUID merchantId, UUID subscriptionId) {
        WebhookSubscription subscription = subscriptionRepository.findBySubscriptionIdAndMerchantId(subscriptionId, merchantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        Problems.notFound("SUBSCRIPTION_NOT_FOUND", "Subscription not found")));
        return WebhookSubscriptionResponse.fromEntity(subscription, null);
    }

    public WebhookSubscriptionResponse updateSubscription(UUID merchantId, UUID subscriptionId, UpdateSubscriptionRequest request) {
        WebhookSubscription subscription = subscriptionRepository.findBySubscriptionIdAndMerchantId(subscriptionId, merchantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        Problems.notFound("SUBSCRIPTION_NOT_FOUND", "Subscription not found")));

        if (request.getUrl() != null && !request.getUrl().isBlank()) {
            urlSafetyValidator.validate(request.getUrl());
            subscription.setUrl(request.getUrl().trim());
        }

        if (request.getEventTypes() != null && !request.getEventTypes().isEmpty()) {
            subscription.setEventTypes(String.join(",", request.getEventTypes()));
        }

        if (request.getActive() != null) {
            subscription.setActive(request.getActive());
            if (request.getActive()) {
                subscription.setPausedReason(null);
            }
        }

        subscription.setUpdatedAt(clock.instant());
        WebhookSubscription saved = subscriptionRepository.save(subscription);
        log.info("Updated webhook subscription id={} for merchantId={}", saved.getSubscriptionId(), merchantId);
        return WebhookSubscriptionResponse.fromEntity(saved, null);
    }

    public void deleteSubscription(UUID merchantId, UUID subscriptionId) {
        WebhookSubscription subscription = subscriptionRepository.findBySubscriptionIdAndMerchantId(subscriptionId, merchantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        Problems.notFound("SUBSCRIPTION_NOT_FOUND", "Subscription not found")));
        subscriptionRepository.delete(subscription);
        log.info("Deleted webhook subscription id={} for merchantId={}", subscriptionId, merchantId);
    }

    public WebhookSubscriptionResponse rotateSecret(UUID merchantId, UUID subscriptionId) {
        WebhookSubscription subscription = subscriptionRepository.findBySubscriptionIdAndMerchantId(subscriptionId, merchantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        Problems.notFound("SUBSCRIPTION_NOT_FOUND", "Subscription not found")));

        byte[] secretBytes = new byte[32];
        RANDOM.nextBytes(secretBytes);
        String rawSecret = "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        String encryptedSecret = crypto.encrypt(rawSecret);

        subscription.setSecretCiphertext(encryptedSecret);
        subscription.setUpdatedAt(clock.instant());
        WebhookSubscription saved = subscriptionRepository.save(subscription);
        log.info("Rotated secret for webhook subscription id={} for merchantId={}", subscriptionId, merchantId);
        return WebhookSubscriptionResponse.fromEntity(saved, rawSecret);
    }

    public WebhookDeliveryResponse testSubscription(UUID merchantId, UUID subscriptionId) {
        WebhookSubscription subscription = subscriptionRepository.findBySubscriptionIdAndMerchantId(subscriptionId, merchantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        Problems.notFound("SUBSCRIPTION_NOT_FOUND", "Subscription not found")));

        Instant now = clock.instant();
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();

        Map<String, Object> payloadMap = Map.of(
                "eventId", eventId.toString(),
                "eventType", "parcel.status-changed",
                "occurredAt", now.toString(),
                "data", Map.of(
                        "parcelId", parcelId.toString(),
                        "trackingNumber", "SD-TEST-000000",
                        "status", "DELIVERED",
                        "previousStatus", "OUT_FOR_DELIVERY",
                        "failureReason", "",
                        "changedAt", now.toString()
                )
        );

        String payloadJson;
        try {
            payloadJson = jsonMapper.writeValueAsString(payloadMap);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize test webhook payload", e);
        }

        WebhookDelivery delivery = new WebhookDelivery(
                UUID.randomUUID(),
                subscription.getSubscriptionId(),
                eventId,
                "parcel.status-changed",
                payloadJson,
                WebhookDeliveryStatus.PENDING,
                0,
                now,
                null,
                null,
                now,
                null
        );

        WebhookDelivery saved = deliveryRepository.save(delivery);
        log.info("Enqueued test webhook delivery id={} for subscription id={}", saved.getDeliveryId(), subscriptionId);
        return WebhookDeliveryResponse.fromEntity(saved, true);
    }

    @Transactional(readOnly = true)
    public Page<WebhookDeliveryResponse> listDeliveries(UUID merchantId, UUID subscriptionId, Pageable pageable) {
        // verify ownership
        subscriptionRepository.findBySubscriptionIdAndMerchantId(subscriptionId, merchantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        Problems.notFound("SUBSCRIPTION_NOT_FOUND", "Subscription not found")));

        return deliveryRepository.findBySubscriptionId(subscriptionId, pageable)
                .map(d -> WebhookDeliveryResponse.fromEntity(d, false));
    }

    @Transactional(readOnly = true)
    public WebhookDeliveryResponse getDelivery(UUID merchantId, UUID deliveryId) {
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        Problems.notFound("DELIVERY_NOT_FOUND", "Delivery not found")));

        // check subscription ownership to prevent enumeration
        subscriptionRepository.findBySubscriptionIdAndMerchantId(delivery.getSubscriptionId(), merchantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        Problems.notFound("DELIVERY_NOT_FOUND", "Delivery not found")));

        return WebhookDeliveryResponse.fromEntity(delivery, true);
    }

    public WebhookDeliveryResponse retryDelivery(UUID merchantId, UUID deliveryId) {
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        Problems.notFound("DELIVERY_NOT_FOUND", "Delivery not found")));

        // check subscription ownership to prevent enumeration
        subscriptionRepository.findBySubscriptionIdAndMerchantId(delivery.getSubscriptionId(), merchantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        Problems.notFound("DELIVERY_NOT_FOUND", "Delivery not found")));

        if (delivery.getStatus() != WebhookDeliveryStatus.FAILED) {
            throw new ApiException(HttpStatus.CONFLICT, "DELIVERY_NOT_RETRYABLE",
                    "Only FAILED deliveries can be retried. Current status is " + delivery.getStatus());
        }

        Instant now = clock.instant();
        delivery.setStatus(WebhookDeliveryStatus.PENDING);
        delivery.setNextAttemptAt(now);
        delivery.setAttemptCount(0);
        delivery.setLastError(null);
        WebhookDelivery saved = deliveryRepository.save(delivery);
        log.info("Merchant {} manually requested retry for delivery {}", merchantId, deliveryId);
        return WebhookDeliveryResponse.fromEntity(saved, true);
    }

    @Transactional(readOnly = true)
    public Page<WebhookDeliveryResponse> listFailedDeliveriesForDispatcher(Pageable pageable) {
        return deliveryRepository.findByStatus(WebhookDeliveryStatus.FAILED, pageable)
                .map(d -> WebhookDeliveryResponse.fromEntity(d, false));
    }

    public void pauseSubscriptions(UUID merchantId, String reason) {
        List<WebhookSubscription> activeList = subscriptionRepository.findByMerchantIdAndActiveTrue(merchantId);
        Instant now = clock.instant();
        for (WebhookSubscription sub : activeList) {
            sub.setActive(false);
            sub.setPausedReason(reason);
            sub.setUpdatedAt(now);
            subscriptionRepository.save(sub);
        }
        log.info("Paused {} webhook subscriptions for merchantId={} with reason={}", activeList.size(), merchantId, reason);
    }

    public void resumeSubscriptions(UUID merchantId, String reason) {
        List<WebhookSubscription> all = subscriptionRepository.findByMerchantId(merchantId);
        Instant now = clock.instant();
        int resumed = 0;
        for (WebhookSubscription sub : all) {
            if (reason.equals(sub.getPausedReason())) {
                sub.setActive(true);
                sub.setPausedReason(null);
                sub.setUpdatedAt(now);
                subscriptionRepository.save(sub);
                resumed++;
            }
        }
        log.info("Resumed {} webhook subscriptions for merchantId={} with reason={}", resumed, merchantId, reason);
    }
}
