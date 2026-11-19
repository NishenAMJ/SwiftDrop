package com.swiftdrop.parcel_pricing_service.exception;

/**
 * External API credentials are missing or not valid. The message is deliberately
 * generic and never contains the credential that was sent.
 */
public class InvalidApiKeyException extends RuntimeException {

    public InvalidApiKeyException() {
        super("Invalid or missing API credentials");
    }
}
