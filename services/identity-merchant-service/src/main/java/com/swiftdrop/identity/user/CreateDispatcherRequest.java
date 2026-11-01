package com.swiftdrop.identity.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code initialPassword} is checked by {@link PasswordPolicy}. */
public record CreateDispatcherRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 100) String fullName,
        @Pattern(regexp = Validation.PHONE_E164, message = Validation.PHONE_MESSAGE) String phoneNumber,
        @NotNull String initialPassword) {
}
