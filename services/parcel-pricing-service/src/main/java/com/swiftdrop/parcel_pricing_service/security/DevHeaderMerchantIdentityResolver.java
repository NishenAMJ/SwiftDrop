package com.swiftdrop.parcel_pricing_service.security;

import com.swiftdrop.parcel_pricing_service.exception.UnauthenticatedMerchantException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * LOCAL TESTING ONLY (profile {@code dev}). Trusts the {@code X-Merchant-Id} header, which
 * anyone can forge, so it must never run outside a developer machine. Replace with a
 * resolver backed by real authentication.
 */
@Component
@Profile("dev")
public class DevHeaderMerchantIdentityResolver implements MerchantIdentityResolver {

    private static final Logger log = LoggerFactory.getLogger(DevHeaderMerchantIdentityResolver.class);
    static final String HEADER = "X-Merchant-Id";

    public DevHeaderMerchantIdentityResolver() {
        log.warn("DEV merchant identity resolver is active: merchant identity is taken from the "
                + "{} header WITHOUT authentication. Never use the 'dev' profile in production.", HEADER);
    }

    @Override
    public Long resolveMerchantId(HttpServletRequest request) {
        String value = request.getHeader(HEADER);
        if (value == null || value.isBlank()) {
            throw new UnauthenticatedMerchantException();
        }
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) {
                throw new UnauthenticatedMerchantException();
            }
            return id;
        } catch (NumberFormatException ex) {
            throw new UnauthenticatedMerchantException();
        }
    }
}
