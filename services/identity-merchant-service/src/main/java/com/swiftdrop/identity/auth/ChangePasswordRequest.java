package com.swiftdrop.identity.auth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code currentPassword} may be omitted only by an account that has no password yet. */
public record ChangePasswordRequest(
        @Size(max = 128) String currentPassword,
        @NotNull String newPassword) {
}
