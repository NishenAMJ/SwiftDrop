package com.swiftdrop.communication.webhook;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.swiftdrop.communication.config.WebhookProperties;

@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    /** Must match webhook_deliveries.last_error VARCHAR(200), or saving the delivery fails. */
    static final int MAX_ERROR_LENGTH = 200;

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookSecretCrypto crypto;
    private final WebhookSigner signer;
    private final UrlSafetyValidator urlSafetyValidator;
    private final WebhookProperties properties;
    private final Clock clock;
    private final RestClient restClient;
    private final TransactionTemplate transactionTemplate;

    public WebhookDispatcher(WebhookDeliveryRepository deliveryRepository,
                             WebhookSubscriptionRepository subscriptionRepository,
                             WebhookSecretCrypto crypto,
                             WebhookSigner signer,
                             UrlSafetyValidator urlSafetyValidator,
                             WebhookProperties properties,
                             Clock clock,
                             PlatformTransactionManager transactionManager) {
        this.deliveryRepository = deliveryRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.crypto = crypto;
        this.signer = signer;
        this.urlSafetyValidator = urlSafetyValidator;
        this.properties = properties;
        this.clock = clock;

        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();

        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${swiftdrop.webhook.poll-interval:5s}")
    public void pollAndDispatch() {
        List<UUID> dueDeliveryIds = fetchDueDeliveryIds();
        if (dueDeliveryIds.isEmpty()) {
            return;
        }

        log.debug("Found {} due webhook deliveries to process", dueDeliveryIds.size());
        for (UUID deliveryId : dueDeliveryIds) {
            try {
                processSingleDelivery(deliveryId);
            } catch (Exception e) {
                log.error("Unexpected error processing webhook delivery id={}", deliveryId, e);
            }
        }
    }

    private List<UUID> fetchDueDeliveryIds() {
        TransactionTemplate readTemplate = new TransactionTemplate(transactionTemplate.getTransactionManager());
        readTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return readTemplate.execute(status -> {
            List<WebhookDelivery> deliveries = deliveryRepository.findDuePendingDeliveriesForUpdate(clock.instant(), 20);
            return deliveries.stream().map(WebhookDelivery::getDeliveryId).toList();
        });
    }

    public void processSingleDelivery(UUID deliveryId) {
        TransactionTemplate tx = new TransactionTemplate(transactionTemplate.getTransactionManager());
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        tx.executeWithoutResult(status -> {
            Optional<WebhookDelivery> optDelivery = deliveryRepository.findById(deliveryId);
            if (optDelivery.isEmpty()) {
                return;
            }
            WebhookDelivery delivery = optDelivery.get();
            if (delivery.getStatus() != WebhookDeliveryStatus.PENDING) {
                return;
            }

            Optional<WebhookSubscription> optSub = subscriptionRepository.findById(delivery.getSubscriptionId());
            if (optSub.isEmpty()) {
                log.warn("Subscription not found for delivery id={}, marking FAILED", deliveryId);
                delivery.setStatus(WebhookDeliveryStatus.FAILED);
                delivery.setLastError("Subscription not found");
                delivery.setNextAttemptAt(null);
                deliveryRepository.save(delivery);
                return;
            }

            WebhookSubscription subscription = optSub.get();
            if (!subscription.isActive()) {
                log.info("Subscription id={} is inactive ({}), postponing delivery id={}",
                        subscription.getSubscriptionId(), subscription.getPausedReason(), deliveryId);
                delivery.setNextAttemptAt(clock.instant().plus(Duration.ofMinutes(1)));
                deliveryRepository.save(delivery);
                return;
            }

            try {
                urlSafetyValidator.validate(subscription.getUrl());
            } catch (Exception e) {
                log.warn("URL validation failed for delivery id={}: {}", deliveryId, e.getMessage());
                delivery.setStatus(WebhookDeliveryStatus.FAILED);
                delivery.setLastError(truncate("URL validation failed: " + e.getMessage(), MAX_ERROR_LENGTH));
                delivery.setNextAttemptAt(null);
                deliveryRepository.save(delivery);
                return;
            }

            executeDispatch(delivery, subscription);
        });
    }

    private void executeDispatch(WebhookDelivery delivery, WebhookSubscription subscription) {
        String rawSecret;
        try {
            rawSecret = crypto.decrypt(subscription.getSecretCiphertext());
        } catch (RuntimeException e) {
            // Retrying cannot fix a secret that cannot be decrypted (for example a changed key).
            log.warn("Webhook delivery id={} FAILED: subscription secret could not be decrypted",
                    delivery.getDeliveryId());
            delivery.setStatus(WebhookDeliveryStatus.FAILED);
            delivery.setLastError("Secret could not be decrypted");
            delivery.setNextAttemptAt(null);
            deliveryRepository.save(delivery);
            return;
        }
        long timestampSeconds = clock.instant().getEpochSecond();
        String signatureHeader = signer.buildHeader(rawSecret, timestampSeconds, delivery.getPayload());

        int newAttemptCount = delivery.getAttemptCount() + 1;
        delivery.setAttemptCount(newAttemptCount);

        Integer statusCode = null;
        String errorMessage = null;
        boolean success = false;

        try {
            var response = restClient.post()
                    .uri(subscription.getUrl())
                    .header("Content-Type", "application/json")
                    .header("X-SwiftDrop-Event", delivery.getEventType())
                    .header("X-SwiftDrop-Delivery", delivery.getDeliveryId().toString())
                    .header("X-SwiftDrop-Timestamp", String.valueOf(timestampSeconds))
                    .header("X-SwiftDrop-Signature", signatureHeader)
                    .body(delivery.getPayload())
                    .retrieve()
                    .toBodilessEntity();

            statusCode = response.getStatusCode().value();
            success = response.getStatusCode().is2xxSuccessful();
        } catch (RestClientResponseException ex) {
            statusCode = ex.getStatusCode().value();
            // The merchant's response body is not stored: it could contain anything.
            errorMessage = "HTTP " + statusCode;
            log.warn("Webhook delivery id={} to {} returned HTTP {}", delivery.getDeliveryId(), subscription.getUrl(), statusCode);
        } catch (Exception ex) {
            errorMessage = truncate(ex.getClass().getSimpleName() + ": " + ex.getMessage(), MAX_ERROR_LENGTH);
            log.warn("Webhook delivery id={} to {} failed: {}", delivery.getDeliveryId(), subscription.getUrl(), errorMessage);
        }

        Instant now = clock.instant();
        if (success) {
            delivery.setStatus(WebhookDeliveryStatus.DELIVERED);
            delivery.setDeliveredAt(now);
            delivery.setLastStatusCode(statusCode);
            delivery.setLastError(null);
            delivery.setNextAttemptAt(null);
            log.info("Webhook delivery id={} successfully delivered to {} on attempt {}",
                    delivery.getDeliveryId(), subscription.getUrl(), newAttemptCount);
        } else {
            delivery.setLastStatusCode(statusCode);
            delivery.setLastError(errorMessage);

            Optional<Duration> backoff = RetryPolicy.nextDelay(newAttemptCount, properties.getRetryDelays());
            if (backoff.isPresent()) {
                delivery.setNextAttemptAt(now.plus(backoff.get()));
                delivery.setStatus(WebhookDeliveryStatus.PENDING);
                log.info("Webhook delivery id={} scheduled retry #{} in {} ms",
                        delivery.getDeliveryId(), newAttemptCount, backoff.get().toMillis());
            } else {
                delivery.setStatus(WebhookDeliveryStatus.FAILED);
                delivery.setNextAttemptAt(null);
                log.warn("Webhook delivery id={} permanently FAILED after {} attempts",
                        delivery.getDeliveryId(), newAttemptCount);
            }
        }

        deliveryRepository.save(delivery);
    }

    private String truncate(String s, int maxLength) {
        if (s == null) {
            return null;
        }
        return s.length() <= maxLength ? s : s.substring(0, maxLength);
    }
}
