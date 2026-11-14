package com.swiftdrop.tracking.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

class TrackingJwtAuthenticationConverterTests {

    @Test
    void convertsRolesClaimToRoleAuthorities() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("dispatcher-id")
                .issuedAt(Instant.parse("2026-11-14T10:00:00Z"))
                .expiresAt(Instant.parse("2026-11-14T10:15:00Z"))
                .claim("roles", List.of("DISPATCHER"))
                .build();
        JwtAuthenticationConverter converter = new SecurityConfig().jwtAuthenticationConverter();

        var authentication = converter.convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .contains("ROLE_DISPATCHER");
    }
}
