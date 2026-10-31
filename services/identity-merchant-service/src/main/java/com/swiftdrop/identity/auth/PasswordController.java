package com.swiftdrop.identity.auth;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.identity.common.ApiException;
import com.swiftdrop.identity.ratelimit.RateLimits;
import com.swiftdrop.identity.token.AccessTokenService;

/**
 * {@code POST /api/v1/users/me/password}. Lives with auth because it also replaces the refresh
 * cookie: other devices are logged out, this one gets a new cookie and stays logged in.
 */
@RestController
public class PasswordController {

    private final AuthService authService;
    private final RefreshCookies cookies;
    private final RateLimits rateLimits;

    public PasswordController(AuthService authService, RefreshCookies cookies, RateLimits rateLimits) {
        this.authService = authService;
        this.cookies = cookies;
        this.rateLimits = rateLimits;
    }

    @PostMapping("/api/v1/users/me/password")
    ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        rateLimits.beforePasswordChange(userId);
        String refreshToken;
        try {
            refreshToken = authService.changePassword(userId, jwt.getClaimAsString(AccessTokenService.AUTH_CLAIM),
                    request);
        }
        catch (ApiException ex) {
            if ("INVALID_CREDENTIALS".equals(ex.code())) {
                rateLimits.passwordChangeFailed(userId);
            }
            throw ex;
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.issue(refreshToken)).build();
    }
}
