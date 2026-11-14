package com.swiftdrop.parcel_pricing_service.common;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }

    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", message);
    }
}

