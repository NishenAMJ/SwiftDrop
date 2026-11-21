package com.swiftdrop.delivery.courier.parcel;

import java.util.UUID;

/**
 * What delivery events need to know about a parcel.
 *
 * @param customerId the registered customer who booked it; null for bookings without one
 */
public record ParcelRef(UUID parcelId, String trackingNumber, UUID customerId) {
}
