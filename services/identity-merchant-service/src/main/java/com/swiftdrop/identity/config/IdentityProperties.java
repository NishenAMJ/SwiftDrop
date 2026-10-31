package com.swiftdrop.identity.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code swiftdrop.identity} in application.yaml.
 */
@ConfigurationProperties("swiftdrop.identity")
public record IdentityProperties(Jwt jwt, RefreshToken refreshToken, Bootstrap bootstrap) {

    /**
     * @param privateKeyPath PEM file (PKCS#8) with the RSA signing key. Empty means a temporary key.
     */
    public record Jwt(String issuer, String audience, Duration accessTokenTtl, String privateKeyPath) {
    }

    /**
     * @param reuseGrace how long after a rotation the old token is rejected without revoking the
     *     whole family, so two browser tabs refreshing at once don't log the user out
     */
    public record RefreshToken(Duration ttl, String cookieName, String cookiePath, boolean cookieSecure,
            Duration reuseGrace) {
    }

    /**
     * Creates the first dispatcher at startup when none exists.
     */
    public record Bootstrap(String dispatcherEmail, String dispatcherPassword, String dispatcherName) {
    }
}
