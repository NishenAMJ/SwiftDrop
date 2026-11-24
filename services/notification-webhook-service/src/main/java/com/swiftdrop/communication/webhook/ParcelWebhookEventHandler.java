package com.swiftdrop.communication.webhook;

import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.communication.event.EventEnvelope;
import com.swiftdrop.communication.event.ParcelStatusEventData;
import com.swiftdrop.communication.event.ProcessedEvent;
import com.swiftdrop.communication.event.ProcessedEventRepository;

import tools.jackson.databind.json.JsonMapper;

@Component
public class ParcelWebhookEventHandler {

    public static final String CONSUMER = "parcel-webhooks";
    private static final Logger log = LoggerFactory.getLogger(ParcelWebhookEventHandler.class);

    private final ProcessedEventRepository processedEventRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public ParcelWebhookEventHandler(ProcessedEventRepository processedEventRepository,
                                    WebhookSubscriptionRepository subscriptionRepository,
                                    WebhookDeliveryRepository deliveryRepository,
                                    JsonMapper jsonMapper,
                                    Clock clock) {
        this.processedEventRepository = processedEventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional
    public void handle(EventEnvelope envelope) {
        UUID eventId = envelope.getEventId();
        String eventType = envelope.getEventType();

        if (processedEventRepository.existsByEventIdAndConsumer(eventId, CONSUMER)) {
            log.info("Skipping duplicate event for {}: {}", CONSUMER, eventId);
            return;
        }

        ParcelStatusEventData data = jsonMapper.convertValue(envelope.getData(), ParcelStatusEventData.class);
        UUID merchantId = data.getMerchantId();

        if (merchantId == null) {
            log.debug("Parcel event id={} has no merchantId, skipping webhooks", eventId);
            processedEventRepository.save(new ProcessedEvent(eventId, CONSUMER, eventType, clock.instant()));
            return;
        }

        List<WebhookSubscription> activeSubscriptions = subscriptionRepository.findByMerchantIdAndActiveTrue(merchantId);
        List<WebhookSubscription> matchingSubscriptions = activeSubscriptions.stream()
                .filter(sub -> Arrays.stream(sub.getEventTypes().split(","))
                        .map(String::trim)
                        .anyMatch(et -> et.equalsIgnoreCase(eventType) || et.equals("*")))
                .toList();

        if (!matchingSubscriptions.isEmpty()) {
            String payloadJson = buildMerchantPayload(envelope, data);

            for (WebhookSubscription sub : matchingSubscriptions) {
                if (deliveryRepository.existsBySubscriptionIdAndEventId(sub.getSubscriptionId(), eventId)) {
                    log.debug("Delivery already exists for subscription id={} and event id={}", sub.getSubscriptionId(), eventId);
                    continue;
                }

                WebhookDelivery delivery = new WebhookDelivery(
                        UUID.randomUUID(),
                        sub.getSubscriptionId(),
                        eventId,
                        eventType,
                        payloadJson,
                        WebhookDeliveryStatus.PENDING,
                        0,
                        clock.instant(),
                        null,
                        null,
                        clock.instant(),
                        null
                );
                deliveryRepository.save(delivery);
                log.info("Enqueued webhook delivery id={} for subscription id={} on event id={}",
                        delivery.getDeliveryId(), sub.getSubscriptionId(), eventId);
            }
        }

        processedEventRepository.save(new ProcessedEvent(eventId, CONSUMER, eventType, clock.instant()));
    }

    private String buildMerchantPayload(EventEnvelope envelope, ParcelStatusEventData data) {
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("eventId", envelope.getEventId().toString());
        payloadMap.put("eventType", envelope.getEventType());
        payloadMap.put("occurredAt", envelope.getOccurredAt() != null ? envelope.getOccurredAt().toString() : clock.instant().toString());

        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("parcelId", data.getParcelId() != null ? data.getParcelId().toString() : null);
        dataMap.put("trackingNumber", data.getTrackingNumber());
        dataMap.put("status", data.getStatus());
        dataMap.put("previousStatus", data.getPreviousStatus());
        dataMap.put("failureReason", data.getFailureReason());
        dataMap.put("changedAt", data.getChangedAt() != null ? data.getChangedAt().toString() : null);

        payloadMap.put("data", dataMap);

        try {
            return jsonMapper.writeValueAsString(payloadMap);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize merchant webhook payload", e);
        }
    }
}
