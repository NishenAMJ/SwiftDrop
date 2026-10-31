package com.swiftdrop.identity.common;

import java.util.List;

import org.springframework.http.HttpStatus;

/**
 * An expected error that becomes a Problem Details response with a SwiftDrop error code.
 * The message is shown to the caller, so it must never contain internal details.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<FieldError> errors;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    private ApiException(HttpStatus status, String code, String message, List<FieldError> errors) {
        super(message);
        this.status = status;
        this.code = code;
        this.errors = errors;
    }

    public static ApiException validation(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more fields are invalid.",
                List.of(new FieldError(field, message)));
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<FieldError> errors() {
        return errors;
    }

    public record FieldError(String field, String message) {
    }
}
