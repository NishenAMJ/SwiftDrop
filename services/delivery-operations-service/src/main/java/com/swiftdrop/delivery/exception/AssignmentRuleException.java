package com.swiftdrop.delivery.exception;

/** An assignment the rules refuse, with a stable code so screens can explain it without showing server text. */
public class AssignmentRuleException extends InvalidOperationException {

    public static final String PARCEL_CANCELLED = "PARCEL_CANCELLED";
    public static final String PARCEL_TOO_HEAVY = "PARCEL_TOO_HEAVY";

    private final String code;

    public AssignmentRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
