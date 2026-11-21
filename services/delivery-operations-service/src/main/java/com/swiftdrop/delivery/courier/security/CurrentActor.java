package com.swiftdrop.delivery.courier.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Who is making the current request, taken from the verified access token and nowhere else. The audit
 * trail uses this, so a caller cannot choose the name that gets recorded.
 */
@Component
public class CurrentActor {

    /** @param id the token's sub; null when there is no signed-in user (never for an API request) */
    public record Actor(UUID id, String role) {
    }

    public Actor get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return new Actor(subject(jwt), roleOf(jwt));
        }
        return new Actor(null, "SYSTEM");
    }

    private static UUID subject(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return null;
        }
    }

    private static String roleOf(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null && roles.contains("DISPATCHER")) {
            return "DISPATCHER";
        }
        if (roles != null && roles.contains("COURIER")) {
            return "COURIER";
        }
        return "UNKNOWN";
    }
}
