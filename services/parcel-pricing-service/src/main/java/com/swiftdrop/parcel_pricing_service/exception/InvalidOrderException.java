package com.swiftdrop.parcel_pricing_service.exception;

/** A business-rule violation that bean validation cannot express (e.g. COD amount rules). */
public class InvalidOrderException extends RuntimeException {

    public InvalidOrderException(String message) {
        super(message);
    }
}
