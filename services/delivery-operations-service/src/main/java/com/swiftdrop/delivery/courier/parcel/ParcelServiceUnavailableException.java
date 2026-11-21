package com.swiftdrop.delivery.courier.parcel;

public class ParcelServiceUnavailableException extends RuntimeException {
    public ParcelServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
