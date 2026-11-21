package com.swiftdrop.delivery.courier.parcel;

/** Who to meet and where, for one end of a delivery. The courier needs it to do the job. */
public record ParcelContact(String name, String phone, String address) {
}
