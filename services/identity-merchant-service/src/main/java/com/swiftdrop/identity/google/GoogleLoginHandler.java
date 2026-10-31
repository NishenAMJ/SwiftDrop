package com.swiftdrop.identity.google;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.util.UriComponentsBuilder;

import com.swiftdrop.identity.auth.AuthService;
import com.swiftdrop.identity.auth.RefreshCookies;
import com.swiftdrop.identity.common.ApiException;

/**
 * Finishes Google login: sets the refresh cookie and sends the browser to
 * {@code {FRONTEND_BASE_URL}/auth/callback}. The frontend then calls POST /api/v1/auth/refresh for
 * its first access token. On failure it goes to the same page with {@code ?error=<reason>}:
 * {@code email_not_verified}, {@code account_suspended}, {@code account_mismatch} or
 * {@code google_login_failed}. No token ever goes in a URL.
 */
public class GoogleLoginHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleLoginHandler.class);

    private final AuthService authService;
    private final RefreshCookies cookies;
    private final String callbackUrl;

    public GoogleLoginHandler(AuthService authService, RefreshCookies cookies, String frontendBaseUrl) {
        this.authService = authService;
        this.cookies = cookies;
        this.callbackUrl = UriComponentsBuilder.fromUriString(frontendBaseUrl).path("/auth/callback").toUriString();
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        endSession(request);
        if (!(authentication.getPrincipal() instanceof OidcUser google)) {
            redirectWithError(response, "google_login_failed");
            return;
        }
        if (google.getEmail() == null || !Boolean.TRUE.equals(google.getEmailVerified())) {
            redirectWithError(response, "email_not_verified");
            return;
        }
        try {
            String refreshToken = authService.signInWithGoogle(google.getSubject(), google.getEmail(),
                    google.getFullName());
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.issue(refreshToken));
            response.sendRedirect(callbackUrl);
        }
        catch (ApiException ex) {
            redirectWithError(response, switch (ex.code()) {
                case "ACCOUNT_SUSPENDED" -> "account_suspended";
                case "GOOGLE_ACCOUNT_MISMATCH" -> "account_mismatch";
                default -> "google_login_failed";
            });
        }
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        // Usually the user pressed "Cancel" at Google, or the login took too long.
        log.info("Google login failed: {}", exception.getMessage());
        endSession(request);
        redirectWithError(response, "google_login_failed");
    }

    private void redirectWithError(HttpServletResponse response, String error) throws IOException {
        response.sendRedirect(UriComponentsBuilder.fromUriString(callbackUrl).queryParam("error", error).toUriString());
    }

    private static void endSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
