package com.swiftdrop.parcel_pricing_service.exception;

import java.util.UUID;

/** Unknown order, or an order of another merchant (both answer 404). */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID id) {
        super("Merchant order with id " + id + " was not found");
    }
}
