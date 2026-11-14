package com.swiftdrop.payment.onlinepayment.service;

import java.util.*;
import org.springframework.security.oauth2.jwt.Jwt;

public record Actor(UUID id, UUID merchantId, Set<String> roles, String token) {
    public static Actor from(Jwt jwt) {
        try {
            List<String> roles = jwt.getClaimAsStringList("roles");
            String merchant = jwt.getClaimAsString("merchant_id");
            return new Actor(UUID.fromString(jwt.getSubject()), merchant == null ? null : UUID.fromString(merchant),
                roles == null ? Set.of() : Set.copyOf(roles), jwt.getTokenValue());
        } catch (RuntimeException e) { throw PaymentProblem.forbidden(); }
    }
    public boolean admin() { return roles.contains("DISPATCHER"); }
    public boolean owns(UUID customer, UUID merchant) {
        return admin() || (roles.contains("CUSTOMER") && id.equals(customer))
            || (roles.contains("MERCHANT") && merchantId != null && merchantId.equals(merchant));
    }
    public void requireOwner(UUID customer, UUID merchant) {
        if (!owns(customer, merchant)) throw PaymentProblem.forbidden();
    }
    public void requireAdmin() { if (!admin()) throw PaymentProblem.forbidden(); }
}
