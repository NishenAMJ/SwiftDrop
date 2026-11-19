package com.swiftdrop.communication.webhook;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dev/webhook-receiver")
@Profile("dev")
public class DevWebhookReceiverController {

    private static final Logger log = LoggerFactory.getLogger(DevWebhookReceiverController.class);

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookSecretCrypto crypto;
    private final WebhookSigner signer;

    private final AtomicInteger callCount = new AtomicInteger(0);
    private final List<ReceivedWebhook> history = new CopyOnWriteArrayList<>();

    @Value("${swiftdrop.dev.receiver-fail-first:0}")
    private int failFirst;

    public DevWebhookReceiverController(WebhookDeliveryRepository deliveryRepository,
                                        WebhookSubscriptionRepository subscriptionRepository,
                                        WebhookSecretCrypto crypto,
                                        WebhookSigner signer) {
        this.deliveryRepository = deliveryRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.crypto = crypto;
        this.signer = signer;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> receiveWebhook(
            @RequestHeader(value = "X-SwiftDrop-Delivery", required = false) String deliveryIdHeader,
            @RequestHeader(value = "X-SwiftDrop-Event", required = false) String eventHeader,
            @RequestHeader(value = "X-SwiftDrop-Timestamp", required = false) String timestampHeader,
            @RequestHeader(value = "X-SwiftDrop-Signature", required = false) String signatureHeader,
            @RequestBody String payload) {

        log.info("Dev receiver invoked with deliveryId={}, event={}", deliveryIdHeader, eventHeader);

        if (deliveryIdHeader == null || deliveryIdHeader.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing X-SwiftDrop-Delivery header"));
        }

        UUID deliveryId;
        try {
            deliveryId = UUID.fromString(deliveryIdHeader.trim());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid delivery ID in header"));
        }

        Optional<WebhookDelivery> deliveryOpt = deliveryRepository.findById(deliveryId);
        if (deliveryOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Delivery not found: " + deliveryId));
        }

        WebhookDelivery delivery = deliveryOpt.get();
        Optional<WebhookSubscription> subscriptionOpt = subscriptionRepository.findById(delivery.getSubscriptionId());
        if (subscriptionOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Subscription not found for delivery"));
        }

        String rawSecret = crypto.decrypt(subscriptionOpt.get().getSecretCiphertext());
        boolean verified = signer.verify(rawSecret, signatureHeader, payload, 300);
        if (!verified) {
            log.warn("Dev receiver: Signature verification failed for deliveryId={}", deliveryId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid signature"));
        }

        int current = callCount.incrementAndGet();
        if (current <= failFirst) {
            log.info("Dev receiver: Simulating failure {}/{} for deliveryId={}", current, failFirst, deliveryId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Simulated failure " + current + " of " + failFirst));
        }

        ReceivedWebhook entry = new ReceivedWebhook(
                deliveryIdHeader,
                eventHeader,
                timestampHeader,
                signatureHeader,
                payload,
                Instant.now()
        );
        history.add(entry);

        log.info("Dev receiver: Successfully accepted deliveryId={}", deliveryId);
        return ResponseEntity.ok(Map.of("status", "accepted", "deliveryId", deliveryIdHeader));
    }

    @GetMapping("/history")
    public List<ReceivedWebhook> getHistory() {
        return history;
    }

    @DeleteMapping("/history")
    public ResponseEntity<Void> clearHistory() {
        history.clear();
        callCount.set(0);
        return ResponseEntity.noContent().build();
    }

    public record ReceivedWebhook(
            String deliveryId,
            String eventType,
            String timestamp,
            String signature,
            String payload,
            Instant receivedAt
    ) {
    }
}
