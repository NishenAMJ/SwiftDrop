package com.swiftdrop.identity.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import com.swiftdrop.identity.config.IdentityProperties;

/**
 * The {@code sd_refresh} cookie: HttpOnly (JavaScript can't read it), SameSite=Strict (other sites
 * can't make the browser send it) and scoped to /api/v1/auth, so no other endpoint receives it.
 */
@Component
public class RefreshCookies {

    private final IdentityProperties.RefreshToken settings;

    public RefreshCookies(IdentityProperties properties) {
        this.settings = properties.refreshToken();
    }

    public String read(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, settings.cookieName());
        return cookie == null ? null : cookie.getValue();
    }

    public String issue(String rawToken) {
        return base(rawToken).maxAge(settings.ttl()).build().toString();
    }

    public String clear() {
        return base("").maxAge(0).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(settings.cookieName(), value)
            .httpOnly(true)
            .secure(settings.cookieSecure())
            .sameSite("Strict")
            .path(settings.cookiePath());
    }
}
