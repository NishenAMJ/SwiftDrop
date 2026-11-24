package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.entity.BookingStatus;
import com.swiftdrop.parcel_pricing_service.entity.Parcel;
import com.swiftdrop.parcel_pricing_service.event.ParcelEventPublisher;
import com.swiftdrop.parcel_pricing_service.repository.ParcelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps {@code parcels.status} in step with Delivery Operations, so the customer's booking
 * history and dashboard show the same stage as the dispatcher, courier and public tracking.
 *
 * <p>Delivery Operations owns the delivery steps; this service only records them.
 */
@Service
public class ParcelStatusService {

    private static final Logger log = LoggerFactory.getLogger(ParcelStatusService.class);

    /** eventType from contracts/events/delivery-operations to our booking status. */
    static final Map<String, String> DELIVERY_EVENT_STATUS = Map.of(
            "delivery.assigned", BookingStatus.ASSIGNED,
            "delivery.picked-up", BookingStatus.PICKED_UP,
            "parcel.received-at-warehouse", BookingStatus.AT_WAREHOUSE,
            "parcel.transferred", BookingStatus.IN_TRANSIT,
            "delivery.in-transit", BookingStatus.IN_TRANSIT,
            "delivery.out-for-delivery", BookingStatus.OUT_FOR_DELIVERY,
            "delivery.delivered", BookingStatus.DELIVERED,
            "delivery.failed", BookingStatus.DELIVERY_FAILED);

    private final ParcelRepository parcels;
    private final ParcelEventPublisher events;

    public ParcelStatusService(ParcelRepository parcels, ParcelEventPublisher events) {
        this.parcels = parcels;
        this.events = events;
    }

    public static Optional<String> statusFor(String eventType) {
        return Optional.ofNullable(DELIVERY_EVENT_STATUS.get(eventType));
    }

    /**
     * @return true when the booking status changed
     */
    @Transactional
    public boolean applyDeliveryUpdate(UUID parcelId, String trackingNumber, String newStatus, String failureCode) {
        Optional<Parcel> found = parcelId != null ? parcels.lockById(parcelId) : Optional.empty();
        if (found.isEmpty() && trackingNumber != null) found = parcels.lockByTrackingNumber(trackingNumber);
        if (found.isEmpty()) {
            // For example a merchant order that is not a customer booking. Nothing to update here.
            log.info("Delivery update {} for unknown parcel {} / {} ignored", newStatus, parcelId, trackingNumber);
            return false;
        }
        Parcel parcel = found.get();
        String current = parcel.getStatus();
        if (BookingStatus.CANCELLED.equals(current)) {
            log.warn("Delivery update {} received for cancelled booking {}; Delivery Operations should stop it",
                    newStatus, parcel.getTrackingNumber());
            return false;
        }
        if (!BookingStatus.canMove(current, newStatus)) {
            log.debug("Booking {} stays {} (ignoring older or repeated {})", parcel.getTrackingNumber(), current, newStatus);
            return false;
        }
        parcel.setStatus(newStatus);
        parcel.setStatusUpdatedAt(OffsetDateTime.now());
        parcel.setUpdatedAt(OffsetDateTime.now());
        events.statusChanged(parcel, current, newStatus, failureReason(failureCode));
        return true;
    }

    private static String failureReason(String failureCode) {
        if (failureCode == null) return null;
        return switch (failureCode) {
            case "RECIPIENT_UNAVAILABLE" -> "The recipient was not available. We will try again.";
            case "ADDRESS_NOT_FOUND" -> "The courier could not find the delivery address.";
            case "REFUSED" -> "The recipient refused the parcel.";
            default -> "The delivery attempt was not successful. We will contact you.";
        };
    }
}
