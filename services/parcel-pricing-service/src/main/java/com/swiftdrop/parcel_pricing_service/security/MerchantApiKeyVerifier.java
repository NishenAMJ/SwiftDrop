package com.swiftdrop.parcel_pricing_service.security;

import com.swiftdrop.parcel_pricing_service.exception.ApiKeyVerificationUnavailableException;

import java.util.Optional;

/**
 * Verifies a merchant API key. Identity (Member 2) owns key generation, storage and verification;
 * this service never stores, generates or logs keys. See {@code IdentityApiKeyVerifier}.
 */
@FunctionalInterface
public interface MerchantApiKeyVerifier {

    /**
     * @param apiKey the raw key sent by the caller (never log it)
     * @return the merchant that owns the key, or empty if the key is unknown, revoked or expired,
     *         or the merchant is not active
     * @throws ApiKeyVerificationUnavailableException if no decision can be made (Identity down,
     *         unexpected answer); the request is then rejected with 503
     */
    Optional<MerchantPrincipal> verify(String apiKey);
}
