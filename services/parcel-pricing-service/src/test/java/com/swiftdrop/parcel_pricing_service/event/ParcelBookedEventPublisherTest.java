package com.swiftdrop.parcel_pricing_service.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ParcelBookedEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void publishesVersionOneCustomerSafePersistentEvent() throws Exception {
        var objectMapper = JsonMapper.builder().build();
        var publisher = new ParcelBookedEventPublisher(rabbitTemplate, objectMapper);
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();

        publisher.publish(new ParcelBookedEvent(
                eventId,
                Instant.parse("2026-11-15T10:00:00Z"),
                parcelId,
                "SWD-2026-000001",
                "Colombo"));

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
                eq("swiftdrop.parcel-pricing"),
                eq("parcel.booked"),
                messageCaptor.capture());

        Message message = messageCaptor.getValue();
        assertEquals(eventId.toString(), message.getMessageProperties().getMessageId());
        assertEquals("application/json", message.getMessageProperties().getContentType());
        assertEquals(MessageDeliveryMode.PERSISTENT, message.getMessageProperties().getDeliveryMode());

        var json = objectMapper.readTree(message.getBody());
        assertEquals(eventId.toString(), json.path("eventId").asText());
        assertEquals("parcel.booked", json.path("eventType").asText());
        assertEquals(1, json.path("version").asInt());
        assertEquals("parcel-pricing-service", json.path("producer").asText());
        assertEquals(parcelId.toString(), json.path("data").path("parcelId").asText());
        assertEquals("SWD-2026-000001", json.path("data").path("trackingNumber").asText());
        assertEquals("BOOKED", json.path("data").path("deliveryStatus").asText());
        assertEquals("Parcel booking confirmed", json.path("data").path("publicDescription").asText());
        assertEquals("Colombo", json.path("data").path("publicLocation").asText());
        assertFalse(json.toString().contains("contactName"));
        assertFalse(json.toString().contains("contactPhone"));
        assertFalse(json.toString().contains("streetAddress"));
        assertNotNull(json.path("occurredAt").textValue());
    }
}
