package com.swiftdrop.identity.token;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.swiftdrop.identity.config.IdentityProperties;
import com.swiftdrop.identity.user.User;

/**
 * Issues access tokens with the claims from docs/architecture/authentication.md.
 */
@Service
public class AccessTokenService {

    public static final String ROLES_CLAIM = "roles";
    public static final String AUTH_CLAIM = "auth";

    private final JwtEncoder encoder;
    private final JwtKeys keys;
    private final IdentityProperties.Jwt settings;
    private final Clock clock;

    public AccessTokenService(JwtEncoder encoder, JwtKeys keys, IdentityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.keys = keys;
        this.settings = properties.jwt();
        this.clock = clock;
    }

    /**
     * @param authMethod {@code password}, {@code google} or {@code api_key}
     */
    public IssuedAccessToken issue(User user, String authMethod) {
        Instant now = clock.instant();
        Duration ttl = settings.accessTokenTtl();

        // A dispatcher who still has the initial password gets no roles: they can only read their
        // own account and change the password until they do.
        List<String> roles = user.isMustChangePassword()
                ? List.of()
                : user.roleNames().stream().map(Enum::name).toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(settings.issuer())
            .audience(List.of(settings.audience()))
            .subject(user.getId().toString())
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .id(UUID.randomUUID().toString())
            .claim(ROLES_CLAIM, roles)
            .claim(AUTH_CLAIM, authMethod)
            .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keys.keyId()).type("JWT").build();

        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, ttl.toSeconds());
    }

    public record IssuedAccessToken(String value, long expiresInSeconds) {
    }
}
