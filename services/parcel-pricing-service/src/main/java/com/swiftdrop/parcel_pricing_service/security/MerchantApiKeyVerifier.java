package com.swiftdrop.parcel_pricing_service.security;

import com.swiftdrop.parcel_pricing_service.exception.ApiKeyVerificationUnavailableException;

import java.util.Optional;

/**
 * Integration point for Member 2's API key verification (see
 * contracts/merchant-api-key-verification.md).
 *
 * <p>Member 2 owns key generation, storage, verification and the key-to-merchant mapping;
 * this module only calls this interface and never stores, generates or logs keys.
 * Implement it (for example as a REST client to identity-merchant-service, never a direct
 * database read) and expose it as a Spring bean. Until then
 * {@link MerchantSecurityDefaults} supplies a fail-closed version, so unauthenticated
 * orders can never be created.
 */
@FunctionalInterface
public interface MerchantApiKeyVerifier {

    /**
     * @param apiKey the raw key sent by the caller (never log it)
     * @return the id of the merchant that owns the key, or empty if the key is unknown,
     *         revoked or expired
     * @throws ApiKeyVerificationUnavailableException if no decision can be made
     *         (verifier down, misconfigured, ...); the request is then rejected with 503
     */
    Optional<Long> verify(String apiKey);
}
