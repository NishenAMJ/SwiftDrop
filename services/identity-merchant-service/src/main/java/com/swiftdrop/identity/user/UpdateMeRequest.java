package com.swiftdrop.identity.user;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMeRequest(
        @Size(min = 1, max = 100) @Pattern(regexp = Validation.NOT_BLANK_TEXT, message = "must not be blank")
        String fullName,

        @Pattern(regexp = Validation.PHONE_E164, message = Validation.PHONE_MESSAGE)
        String phoneNumber) {

    @AssertTrue(message = "send at least one field to change")
    public boolean isAnyFieldPresent() {
        return fullName != null || phoneNumber != null;
    }
}
