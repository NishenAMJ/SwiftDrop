package com.swiftdrop.parcel_pricing_service.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long id) {
        super("Merchant order with id " + id + " was not found");
    }
}
