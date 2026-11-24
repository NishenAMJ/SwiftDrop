package com.swiftdrop.delivery.courier.security;

import com.swiftdrop.delivery.courier.service.CourierService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Ownership rules on top of the role rules in SecurityConfig: a courier only sees and changes their
 * own assignments and issues, while a dispatcher sees everything.
 */
@Component
public class CourierAccess {

    private final CourierService couriers;

    public CourierAccess(CourierService couriers) {
        this.couriers = couriers;
    }

    public boolean isDispatcher(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains("DISPATCHER");
    }

    /** The delivery courier profile of the signed-in user. */
    public UUID callerCourierId(Jwt jwt) {
        return couriers.findIdByIdentityUser(subject(jwt))
                .orElseThrow(() -> new AccessDeniedException("No courier profile for this user"));
    }

    /** Dispatchers may name any courier (or none); couriers are limited to themselves. */
    public UUID scopeToCaller(Jwt jwt, UUID requestedCourierId) {
        if (isDispatcher(jwt)) {
            return requestedCourierId;
        }
        var own = callerCourierId(jwt);
        if (requestedCourierId != null && !requestedCourierId.equals(own)) {
            throw new AccessDeniedException("Couriers can only see their own records");
        }
        return own;
    }

    public void requireOwner(Jwt jwt, UUID courierId) {
        if (!callerCourierId(jwt).equals(courierId)) {
            throw new AccessDeniedException("This record belongs to another courier");
        }
    }

    public void requireOwnerOrDispatcher(Jwt jwt, UUID courierId) {
        if (!isDispatcher(jwt)) {
            requireOwner(jwt, courierId);
        }
    }

    public void requireSelfOrDispatcher(Jwt jwt, UUID identityUserId) {
        if (!isDispatcher(jwt) && !subject(jwt).equals(identityUserId)) {
            throw new AccessDeniedException("Couriers can only look up their own profile");
        }
    }

    private UUID subject(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new AccessDeniedException("Token has no valid subject");
        }
    }
}
