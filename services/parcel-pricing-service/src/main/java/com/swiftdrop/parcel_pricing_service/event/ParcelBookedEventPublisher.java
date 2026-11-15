package com.swiftdrop.parcel_pricing_service.event;

import static com.swiftdrop.parcel_pricing_service.config.RabbitMqPublisherConfig.PARCEL_PRICING_EXCHANGE;

import java.time.Instant;
import java.util.UUID;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class ParcelBookedEventPublisher {

    static final String ROUTING_KEY = "parcel.booked";
    static final String PRODUCER = "parcel-pricing-service";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public ParcelBookedEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ParcelBookedEvent event) {
        var envelope = new EventEnvelope(
                event.eventId(),
                ROUTING_KEY,
                1,
                event.occurredAt(),
                PRODUCER,
                new EventData(
                        event.parcelId(),
                        event.trackingNumber(),
                        "BOOKED",
                        "Parcel booking confirmed",
                        event.publicLocation()));

        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setMessageId(event.eventId().toString());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);

        try {
            rabbitTemplate.send(
                    PARCEL_PRICING_EXCHANGE,
                    ROUTING_KEY,
                    new Message(objectMapper.writeValueAsBytes(envelope), properties));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize parcel.booked event", exception);
        }
    }

    record EventEnvelope(
            UUID eventId,
            String eventType,
            int version,
            Instant occurredAt,
            String producer,
            EventData data) {
    }

    record EventData(
            UUID parcelId,
            String trackingNumber,
            String deliveryStatus,
            String publicDescription,
            String publicLocation) {
    }
}
