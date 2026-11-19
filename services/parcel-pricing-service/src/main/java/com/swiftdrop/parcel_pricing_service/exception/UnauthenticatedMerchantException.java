package com.swiftdrop.parcel_pricing_service.exception;

/** The dashboard request does not carry a valid merchant identity. */
public class UnauthenticatedMerchantException extends RuntimeException {

    public UnauthenticatedMerchantException() {
        super("Authentication is required");
    }
}
