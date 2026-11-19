package com.swiftdrop.parcel_pricing_service.exception;

/** Thrown when an inactive zone is used where an active one is required (e.g. pricing). */
public class ZoneInactiveException extends RuntimeException {

    public ZoneInactiveException(Long id) {
        super("Delivery zone with id " + id + " is inactive");
    }
}
