package com.swiftdrop.identity.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.swiftdrop.identity.user.Validation;

/** {@code password} is checked by PasswordPolicy. */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull String password,
        @NotBlank @Size(max = 100) String fullName,
        @Pattern(regexp = Validation.PHONE_E164, message = Validation.PHONE_MESSAGE) String phoneNumber) {
}
