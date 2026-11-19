package com.swiftdrop.parcel_pricing_service.security;

import com.swiftdrop.parcel_pricing_service.exception.UnauthenticatedMerchantException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Integration point: who is the authenticated merchant behind a dashboard request?
 *
 * <p>Implement this with the service's real authentication (for example by reading the
 * merchant id from the verified JWT issued by identity-merchant-service) and expose it as a
 * Spring bean. It must only return an id for a verified user that has the MERCHANT role.
 * Without an implementation, {@link MerchantSecurityDefaults} rejects every dashboard request.
 */
@FunctionalInterface
public interface MerchantIdentityResolver {

    /**
     * @return the id of the authenticated merchant
     * @throws UnauthenticatedMerchantException if the request is not authenticated as a merchant
     */
    Long resolveMerchantId(HttpServletRequest request);
}
