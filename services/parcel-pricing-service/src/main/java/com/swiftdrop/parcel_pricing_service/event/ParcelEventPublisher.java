package com.swiftdrop.parcel_pricing_service.event;

import com.swiftdrop.parcel_pricing_service.entity.BookingStatus;
import com.swiftdrop.parcel_pricing_service.entity.Parcel;
import com.swiftdrop.parcel_pricing_service.entity.ParcelAddress;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds events in the shared envelope (contracts/events/envelope.schema.json) and stores them in
 * the outbox inside the caller's transaction. {@link OutboxPublisher} sends them after commit, so
 * other services only ever hear about bookings that were really saved.
 */
@Component
public class ParcelEventPublisher {

    private final OutboxEventRepository outbox;
    private final JsonMapper json;

    public ParcelEventPublisher(OutboxEventRepository outbox, JsonMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    /**
     * A booking is confirmed (COD at creation, ONLINE after verified payment).
     * Sends parcel.booked for Tracking and parcel.status-changed (CREATED) for Notification.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void bookingConfirmed(Parcel parcel) {
        Map<String, Object> booked = new LinkedHashMap<>();
        booked.put("parcelId", parcel.getId().toString());
        booked.put("trackingNumber", parcel.getTrackingNumber());
        booked.put("deliveryStatus", BookingStatus.BOOKED);
        booked.put("publicDescription", "URGENT".equals(parcel.getUrgency())
                ? "Urgent booking confirmed. Waiting for courier pickup."
                : "Booking confirmed. Waiting for courier pickup.");
        putPublicLocation(booked, parcel.getPickupAddress());
        save(EventNames.PARCEL_PRICING_EXCHANGE, EventNames.PARCEL_BOOKED, booked);

        statusChanged(parcel, null, BookingStatus.BOOKED, null);
    }

    /**
     * The parcel moved to a new delivery stage. Only Parcel & Pricing knows who booked it, so it
     * tells Notification (which needs customerUserId to email the customer).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void statusChanged(Parcel parcel, String previousStatus, String newStatus, String failureReason) {
        String status = notificationStatus(newStatus);
        if (status == null) return;
        String previous = notificationStatus(previousStatus);
        if (status.equals(previous)) return;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("parcelId", parcel.getId().toString());
        data.put("trackingNumber", parcel.getTrackingNumber());
        data.put("status", status);
        if (previous != null) data.put("previousStatus", previous);
        if (parcel.getCustomerId() != null) data.put("customerUserId", parcel.getCustomerId().toString());
        if (parcel.getMerchantId() != null) data.put("merchantId", parcel.getMerchantId().toString());
        if ("FAILED".equals(status) && failureReason != null) data.put("failureReason", failureReason);
        data.put("changedAt", Instant.now().toString());
        save(EventNames.PARCEL_EXCHANGE, EventNames.PARCEL_STATUS_CHANGED, data);
    }

    /** The customer cancelled before courier scheduling. Payment can use it to start the refund. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void bookingCancelled(Parcel parcel) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("parcelId", parcel.getId().toString());
        data.put("trackingNumber", parcel.getTrackingNumber());
        data.put("deliveryStatus", BookingStatus.CANCELLED);
        data.put("publicDescription", "Booking cancelled by the sender.");
        data.put("customerUserId", parcel.getCustomerId().toString());
        data.put("paymentMethod", parcel.getPaymentMethod());
        if (parcel.getPaymentId() != null) data.put("paymentId", parcel.getPaymentId().toString());
        if (parcel.getCancellationFee() != null) data.put("cancellationFee", parcel.getCancellationFee());
        if (parcel.getRefundAmount() != null) data.put("refundAmount", parcel.getRefundAmount());
        data.put("cancelledAt", parcel.getCancelledAt() != null
                ? parcel.getCancelledAt().toInstant().toString() : Instant.now().toString());
        save(EventNames.PARCEL_PRICING_EXCHANGE, EventNames.PARCEL_CANCELLED, data);
    }

    /** Maps our status to the enum in contracts/events/parcel/parcel-status-changed.schema.json. */
    static String notificationStatus(String status) {
        if (status == null) return null;
        return switch (status) {
            case BookingStatus.BOOKED -> "CREATED";
            case BookingStatus.PICKED_UP -> "PICKED_UP";
            case BookingStatus.AT_WAREHOUSE, BookingStatus.IN_TRANSIT -> "IN_TRANSIT";
            case BookingStatus.OUT_FOR_DELIVERY -> "OUT_FOR_DELIVERY";
            case BookingStatus.DELIVERED -> "DELIVERED";
            case BookingStatus.DELIVERY_FAILED -> "FAILED";
            default -> null; // ASSIGNED and CANCELLED are not in the notification contract yet
        };
    }

    /** City only; never a street address (tracking is public). */
    private static void putPublicLocation(Map<String, Object> data, ParcelAddress address) {
        if (address == null || address.getCity() == null || address.getCity().isBlank()) return;
        String city = address.getCity().trim();
        data.put("publicLocation", city.length() > 120 ? city.substring(0, 120) : city);
    }

    private void save(String exchange, String eventType, Map<String, Object> data) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("version", 1);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("producer", EventNames.PRODUCER);
        envelope.put("data", data);
        outbox.save(new OutboxEvent(eventId, exchange, eventType, json.writeValueAsString(envelope)));
    }
}
