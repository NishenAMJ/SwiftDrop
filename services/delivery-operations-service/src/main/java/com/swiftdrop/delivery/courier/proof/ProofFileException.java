package com.swiftdrop.delivery.courier.proof;

import org.springframework.http.HttpStatus;

/** An uploaded proof file the rules refuse, with a stable code so screens can explain it without server text. */
public class ProofFileException extends RuntimeException {

    public static final String FILE_EMPTY = "FILE_EMPTY";
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
    public static final String UNSUPPORTED_FILE_TYPE = "UNSUPPORTED_FILE_TYPE";
    public static final String FILE_UNREADABLE = "FILE_UNREADABLE";

    private final HttpStatus status;
    private final String code;

    public ProofFileException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
