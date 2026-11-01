package com.swiftdrop.identity.user;

/** Shared validation patterns, matching the OpenAPI contract. */
public final class Validation {

    public static final String PHONE_E164 = "^\\+[1-9][0-9]{7,14}$";
    public static final String PHONE_MESSAGE = "must be in international format, for example +94771234567";
    public static final String NOT_BLANK_TEXT = "^(?s).*\\S.*$";

    private Validation() {
    }
}
