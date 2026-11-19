package com.swiftdrop.parcel_pricing_service.exception;

/**
 * API key verification cannot be performed (not implemented yet, or the verifying
 * service is unreachable). Requests are rejected rather than let through.
 */
public class ApiKeyVerificationUnavailableException extends RuntimeException {

    public ApiKeyVerificationUnavailableException() {
        super("External API authentication is currently unavailable");
    }

    public ApiKeyVerificationUnavailableException(Throwable cause) {
        super("External API authentication is currently unavailable", cause);
    }
}
