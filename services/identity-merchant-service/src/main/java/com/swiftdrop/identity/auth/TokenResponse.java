package com.swiftdrop.identity.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.swiftdrop.identity.user.UserResponse;

/** The {@code TokenResponse} schema from the OpenAPI contract. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
}
