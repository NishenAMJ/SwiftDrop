package com.swiftdrop.parcel_pricing_service.event;

import java.time.Instant;
import java.util.UUID;

public record ParcelBookedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID parcelId,
        String trackingNumber,
        String publicLocation) {
}
