package com.swiftdrop.parcel_pricing_service.event;

import com.swiftdrop.parcel_pricing_service.service.ParcelStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

/**
 * Delivery Operations events: courier scheduled, picked up, at warehouse, in transit,
 * out for delivery, delivered, failed. Each one moves the customer's booking status forward.
 */
@Component
@ConditionalOnProperty(name = "swiftdrop.events.listeners.enabled", havingValue = "true", matchIfMissing = true)
public class DeliveryEventListener {

    private static final Logger log = LoggerFactory.getLogger(DeliveryEventListener.class);
    private static final String DELIVERY_PRODUCER = "delivery-operations-service";

    private final JsonMapper json;
    private final EventInbox inbox;
    private final ParcelStatusService statusService;

    public DeliveryEventListener(JsonMapper json, EventInbox inbox, ParcelStatusService statusService) {
        this.json = json;
        this.inbox = inbox;
        this.statusService = statusService;
    }

    @RabbitListener(queues = EventNames.DELIVERY_QUEUE)
    public void onMessage(Message message) {
        IncomingEvent event = IncomingEvent.parse(message.getBody(), json);
        Optional<String> status = ParcelStatusService.statusFor(event.eventType());
        if (status.isEmpty()) {
            log.debug("Ignoring delivery event type {}", event.eventType());
            return;
        }
        if (!DELIVERY_PRODUCER.equals(event.producer())) {
            throw new AmqpRejectAndDontRequeueException("Unexpected producer for " + event.eventType());
        }
        String reported = event.text("deliveryStatus");
        if (reported != null && !reported.equals(status.get())) {
            throw new AmqpRejectAndDontRequeueException("deliveryStatus does not match " + event.eventType());
        }
        inbox.processOnce(event.eventId(), event.eventType(), () -> statusService.applyDeliveryUpdate(
                event.uuid("parcelId"), event.text("trackingNumber"), status.get(), event.text("failureCode")));
    }
}
