package com.swiftdrop.parcel_pricing_service.exception;

public class DuplicateZoneException extends RuntimeException {

    public DuplicateZoneException(String message) {
        super(message);
    }

    public DuplicateZoneException(String message, Throwable cause) {
        super(message, cause);
    }
}
