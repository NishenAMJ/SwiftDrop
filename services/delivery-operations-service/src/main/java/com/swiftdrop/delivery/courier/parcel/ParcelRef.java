package com.swiftdrop.delivery.courier.parcel;

import java.util.UUID;

/** The parcel identifier and tracking number that delivery events must carry. */
public record ParcelRef(UUID parcelId, String trackingNumber) {
}
