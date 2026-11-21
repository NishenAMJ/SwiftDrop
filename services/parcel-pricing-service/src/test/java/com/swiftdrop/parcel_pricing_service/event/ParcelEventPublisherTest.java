package com.swiftdrop.parcel_pricing_service.event;

import com.swiftdrop.parcel_pricing_service.entity.Parcel;
import com.swiftdrop.parcel_pricing_service.entity.ParcelAddress;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The messages must match contracts/events so Tracking and Notification accept them. */
class ParcelEventPublisherTest {

    private final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final ParcelEventPublisher publisher = new ParcelEventPublisher(outbox, json);

    private Parcel parcel() {
        Parcel p = new Parcel();
        p.setCustomerId(UUID.randomUUID());
        p.setTrackingNumber("SWD-2026-000125");
        p.setUrgency("NORMAL");
        p.addAddress(new ParcelAddress("PICKUP", "Nimal", "0771234567", "12 Main St", "Colombo", null, null));
        p.addAddress(new ParcelAddress("DELIVERY", "Kamala", "0777654321", "45 Lake Rd", "Kandy", null, "KANDY"));
        return p;
    }

    private List<OutboxEvent> saved(int expected) {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox, times(expected)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void bookingConfirmedSendsParcelBookedAndStatusChanged() {
        Parcel p = parcel();
        publisher.bookingConfirmed(p);
        List<OutboxEvent> events = saved(2);

        OutboxEvent booked = events.get(0);
        assertEquals("swiftdrop.parcel-pricing", booked.getExchangeName());
        assertEquals("parcel.booked", booked.getRoutingKey());
        JsonNode envelope = json.readTree(booked.getPayload());
        assertEquals(booked.getId().toString(), envelope.path("eventId").asString());
        assertEquals("parcel.booked", envelope.path("eventType").asString());
        assertEquals(1, envelope.path("version").asInt());
        assertEquals("parcel-pricing-service", envelope.path("producer").asString());
        assertFalse(envelope.path("occurredAt").asString().isBlank());
        JsonNode data = envelope.path("data");
        assertEquals(p.getId().toString(), data.path("parcelId").asString());
        assertEquals("SWD-2026-000125", data.path("trackingNumber").asString());
        assertEquals("BOOKED", data.path("deliveryStatus").asString());
        assertEquals("Colombo", data.path("publicLocation").asString());
        // Public tracking gets no personal data: only the fields allowed by the contract.
        assertEquals(List.of("parcelId", "trackingNumber", "deliveryStatus", "publicDescription", "publicLocation"),
                List.copyOf(data.propertyNames()));
        assertFalse(booked.getPayload().contains("12 Main St"));
        assertFalse(booked.getPayload().contains("0771234567"));

        OutboxEvent status = events.get(1);
        assertEquals("swiftdrop.parcel", status.getExchangeName());
        assertEquals("parcel.status-changed", status.getRoutingKey());
        JsonNode statusData = json.readTree(status.getPayload()).path("data");
        assertEquals("CREATED", statusData.path("status").asString());
        assertEquals(p.getCustomerId().toString(), statusData.path("customerUserId").asString());
        assertTrue(statusData.path("previousStatus").isMissingNode());
    }

    @Test
    void deliveryProgressIsMappedToTheNotificationStatuses() {
        Parcel p = parcel();
        publisher.statusChanged(p, "OUT_FOR_DELIVERY", "DELIVERY_FAILED", "Recipient not home");
        JsonNode data = json.readTree(saved(1).get(0).getPayload()).path("data");
        assertEquals("FAILED", data.path("status").asString());
        assertEquals("OUT_FOR_DELIVERY", data.path("previousStatus").asString());
        assertEquals("Recipient not home", data.path("failureReason").asString());
    }

    @Test
    void parcelCancelledMatchesItsContract() {
        Parcel p = parcel();
        p.setPaymentMethod("ONLINE");
        p.setPaymentId(UUID.randomUUID());
        p.setCancellationFee(new java.math.BigDecimal("0.00"));
        p.setRefundAmount(new java.math.BigDecimal("700.00"));
        p.setCancelledAt(java.time.OffsetDateTime.now());
        publisher.bookingCancelled(p);

        OutboxEvent event = saved(1).get(0);
        assertEquals("swiftdrop.parcel-pricing", event.getExchangeName());
        assertEquals("parcel.cancelled", event.getRoutingKey());
        JsonNode data = json.readTree(event.getPayload()).path("data");
        // Exactly the properties allowed by contracts/events/parcel-pricing/parcel-cancelled.schema.json
        List<String> allowed = List.of("parcelId", "trackingNumber", "deliveryStatus", "publicDescription",
                "customerUserId", "paymentMethod", "paymentId", "cancellationFee", "refundAmount", "cancelledAt");
        assertTrue(allowed.containsAll(data.propertyNames()), () -> "unexpected fields: " + data.propertyNames());
        assertEquals("CANCELLED", data.path("deliveryStatus").asString());
        assertEquals(700.0, data.path("refundAmount").asDouble());
        assertFalse(event.getPayload().contains("12 Main St"));
    }

    @Test
    void statusesOutsideTheNotificationContractAreNotSent() {
        Parcel p = parcel();
        publisher.statusChanged(p, "BOOKED", "ASSIGNED", null);
        publisher.statusChanged(p, "AT_WAREHOUSE", "IN_TRANSIT", null);
        verifyNoInteractions(outbox);
    }
}
