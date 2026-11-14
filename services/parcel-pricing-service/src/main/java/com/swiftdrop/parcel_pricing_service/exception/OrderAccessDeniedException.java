package com.swiftdrop.parcel_pricing_service.exception;

/** A merchant tried to read an order that belongs to another merchant. */
public class OrderAccessDeniedException extends RuntimeException {

    public OrderAccessDeniedException() {
        super("You do not have access to this order");
    }
}
