package com.swiftdrop.parcel_pricing_service.security;

import com.swiftdrop.parcel_pricing_service.exception.UnauthenticatedMerchantException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

/**
 * The merchant behind a request, taken from an access token issued by identity-merchant-service.
 *
 * <p>Identity puts the merchant's id in the {@code merchant_id} claim of every MERCHANT token,
 * whether it came from a login or from exchanging an API key ({@code POST /api/v1/auth/token}).
 *
 * @param userId      the merchant account's user id (JWT subject); stored as the parcel's customerId
 *                    so the merchant sees the order in My Bookings and owns it for payment and cancellation
 * @param merchantId  the merchant id (UUID); stored as the parcel's merchantId
 * @param accessToken the verified token, forwarded to Identity to read the merchant's contact details
 */
public record MerchantPrincipal(UUID userId, UUID merchantId, String accessToken) {

    static final String MERCHANT_ID_CLAIM = "merchant_id";
    static final String ROLES_CLAIM = "roles";
    static final String MERCHANT_ROLE = "MERCHANT";

    /**
     * @throws UnauthenticatedMerchantException unless the token belongs to a MERCHANT and carries
     *         both ids
     */
    public static MerchantPrincipal fromJwt(Jwt jwt) {
        if (jwt == null) throw new UnauthenticatedMerchantException();
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        if (roles == null || !roles.contains(MERCHANT_ROLE)) throw new UnauthenticatedMerchantException();
        UUID userId = parse(jwt.getSubject());
        UUID merchantId = parse(jwt.getClaimAsString(MERCHANT_ID_CLAIM));
        if (userId == null || merchantId == null) throw new UnauthenticatedMerchantException();
        return new MerchantPrincipal(userId, merchantId, jwt.getTokenValue());
    }

    private static UUID parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Never print the token. */
    @Override
    public String toString() {
        return "MerchantPrincipal[userId=" + userId + ", merchantId=" + merchantId + "]";
    }
}
