package com.swiftdrop.delivery.courier.parcel;

/** Finds a parcel in Parcel & Pricing from its public tracking number. */
public interface ParcelLookup {

    /**
     * @param trackingNumber the public tracking number, for example SWD-2026-000125
     * @param bearerToken    the caller's access token, forwarded so Parcel & Pricing applies its own checks
     * @throws com.swiftdrop.delivery.exception.ResourceNotFoundException if no such parcel exists
     * @throws ParcelServiceUnavailableException                          if Parcel & Pricing cannot be reached
     */
    ParcelRef find(String trackingNumber, String bearerToken);
}
