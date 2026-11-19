package com.swiftdrop.parcel_pricing_service.exception;

public class ZoneNotFoundException extends RuntimeException {

    public ZoneNotFoundException(Long id) {
        super("Delivery zone with id " + id + " was not found");
    }
}
