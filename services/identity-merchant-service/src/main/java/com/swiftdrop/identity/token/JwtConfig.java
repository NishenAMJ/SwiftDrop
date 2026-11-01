package com.swiftdrop.identity.token;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.swiftdrop.identity.config.IdentityProperties;

/**
 * Signing and verifying access tokens. Identity verifies its own tokens with the public key
 * directly; other services use the JWKS endpoint with the same rules
 * (docs/architecture/authentication.md).
 */
@Configuration
public class JwtConfig {

    @Bean
    JwtEncoder jwtEncoder(JwtKeys keys) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(keys.signingKey())));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtKeys keys, IdentityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey()).build();
        String audience = properties.jwt().audience();
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(audience));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()), audienceValidator,
                new JwtClaimValidator<Object>("identity_version", value -> value instanceof Number n && n.intValue() == 2)));
        return decoder;
    }

    /**
     * How identity itself reads its tokens:
     * <ul>
     *   <li>Person tokens (password, Google): {@code ROLE_*} from the {@code roles} claim, plus
     *       {@code PERSON}.</li>
     *   <li>API-key tokens: only {@code API_KEY}. Inside identity they may read the merchant's own
     *       record and nothing else, so a leaked key can't create more keys or change the owner's
     *       account. Other services read {@code roles} as usual and see a normal MERCHANT token.</li>
     * </ul>
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(AccessTokenService.ROLES_CLAIM);
        roles.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            if (AccessTokenService.API_KEY_AUTH.equals(jwt.getClaimAsString(AccessTokenService.AUTH_CLAIM))) {
                return List.of(new SimpleGrantedAuthority(API_KEY_AUTHORITY));
            }
            List<GrantedAuthority> authorities = new ArrayList<>(roles.convert(jwt));
            authorities.add(new SimpleGrantedAuthority(PERSON_AUTHORITY));
            return authorities;
        });
        return converter;
    }

    public static final String PERSON_AUTHORITY = "PERSON";
    public static final String API_KEY_AUTHORITY = "API_KEY";
}
