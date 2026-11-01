package com.swiftdrop.identity.user;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/** The {@code User} schema from the OpenAPI contract. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String phoneNumber,
        List<RoleName> roles,
        RoleName accountRole,
        String onboardingState,
        UserStatus status,
        List<String> authProviders,
        boolean mustChangePassword,
        UUID merchantId,
        Instant createdAt) {

    public static UserResponse from(User user) {
        List<String> providers = new ArrayList<>();
        if (user.hasPassword()) {
            providers.add("PASSWORD");
        }
        if (user.getGoogleSubject() != null) {
            providers.add("GOOGLE");
        }
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhoneNumber(),
                user.roleNames(), user.getAccountRole(), user.getOnboardingState(), user.getStatus(), providers, user.isMustChangePassword(), user.getMerchantId(),
                user.getCreatedAt());
    }
}
