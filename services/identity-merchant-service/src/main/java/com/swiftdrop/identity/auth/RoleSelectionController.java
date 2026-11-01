package com.swiftdrop.identity.auth;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.identity.token.AccessTokenService;
import com.swiftdrop.identity.user.RoleName;
import com.swiftdrop.identity.user.User;
import com.swiftdrop.identity.user.UserResponse;
import com.swiftdrop.identity.user.UserService;

/** One-time onboarding for a new Google account; never a role-switching endpoint. */
@RestController
public class RoleSelectionController {

    private final UserService users;
    private final AccessTokenService tokens;

    public RoleSelectionController(UserService users, AccessTokenService tokens) {
        this.users = users;
        this.tokens = tokens;
    }

    public record Request(@NotNull RoleName role) {
    }

    @PostMapping("/api/v1/users/me/role")
    public TokenResponse select(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Request request) {
        User user = users.selectRole(UUID.fromString(jwt.getSubject()), request.role());
        var token = tokens.issue(user, jwt.getClaimAsString(AccessTokenService.AUTH_CLAIM));
        return new TokenResponse(token.value(), "Bearer", token.expiresInSeconds(), UserResponse.from(user));
    }
}
