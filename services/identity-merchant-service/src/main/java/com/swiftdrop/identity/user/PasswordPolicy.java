package com.swiftdrop.identity.user;

import java.nio.charset.StandardCharsets;

import com.swiftdrop.identity.common.ApiException;

/**
 * Password rules from the contract: 8 to 64 characters with at least one letter and one digit.
 * BCrypt only reads the first 72 bytes, so longer UTF-8 encodings are rejected rather than
 * silently truncated.
 */
public final class PasswordPolicy {

    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 64;
    static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    /** @throws ApiException {@code VALIDATION_FAILED} naming {@code field} */
    public static void check(String field, String password) {
        String problem = findProblem(password);
        if (problem != null) {
            throw ApiException.validation(field, problem);
        }
    }

    static String findProblem(String password) {
        if (password == null || password.isEmpty()) {
            return "must not be empty";
        }
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            return "must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters";
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return "is too long; use fewer special characters";
        }
        boolean hasLetter = password.codePoints().anyMatch(Character::isLetter);
        boolean hasDigit = password.codePoints().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            return "must contain at least one letter and one digit";
        }
        return null;
    }
}
