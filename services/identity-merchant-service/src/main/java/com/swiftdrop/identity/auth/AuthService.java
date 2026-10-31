package com.swiftdrop.identity.auth;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.identity.common.ApiException;
import com.swiftdrop.identity.token.AccessTokenService;
import com.swiftdrop.identity.token.AccessTokenService.IssuedAccessToken;
import com.swiftdrop.identity.user.User;
import com.swiftdrop.identity.user.UserResponse;
import com.swiftdrop.identity.user.UserService;

/**
 * Login flows: each one ends with an access token (response body) and a refresh token (cookie).
 */
@Service
public class AuthService {

    static final String PASSWORD = "password";

    private final UserService userService;
    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;

    public AuthService(UserService userService, AccessTokenService accessTokens, RefreshTokenService refreshTokens) {
        this.userService = userService;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        User user = userService.registerCustomer(request.email(), request.password(), request.fullName(),
                request.phoneNumber());
        return startSession(user, PASSWORD);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        return startSession(userService.authenticate(request.email(), request.password()), PASSWORD);
    }

    /** New access token plus a rotated refresh token. Roles are re-read from the database. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String rawRefreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(rawRefreshToken);
        User user = userService.get(rotation.userId());
        if (!user.isActive()) {
            refreshTokens.revokeAll(user.getId());
            userService.requireActive(user);
        }
        return new AuthResult(tokenResponse(user, rotation.authMethod()), rotation.rawToken());
    }

    public void logout(String rawRefreshToken) {
        refreshTokens.revoke(rawRefreshToken);
    }

    /**
     * Changes the password and logs out every other device. Returns a fresh refresh token so the
     * device that made the change stays logged in.
     */
    @Transactional
    public String changePassword(UUID userId, String authMethod, ChangePasswordRequest request) {
        if (!PASSWORD.equals(authMethod) && !"google".equals(authMethod)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Sign in as a person to change the password.");
        }
        userService.changePassword(userId, request.currentPassword(), request.newPassword());
        refreshTokens.revokeAll(userId);
        return refreshTokens.create(userId, authMethod);
    }

    private AuthResult startSession(User user, String authMethod) {
        String refreshToken = refreshTokens.create(user.getId(), authMethod);
        return new AuthResult(tokenResponse(user, authMethod), refreshToken);
    }

    private TokenResponse tokenResponse(User user, String authMethod) {
        IssuedAccessToken token = accessTokens.issue(user, authMethod);
        return new TokenResponse(token.value(), "Bearer", token.expiresInSeconds(), UserResponse.from(user));
    }

    /** {@code response} goes in the body, {@code refreshToken} in the cookie. */
    public record AuthResult(TokenResponse response, String refreshToken) {
    }
}
