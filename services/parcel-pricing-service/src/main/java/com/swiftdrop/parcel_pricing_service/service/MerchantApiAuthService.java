package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.exception.InvalidApiKeyException;
import com.swiftdrop.parcel_pricing_service.security.MerchantApiKeyVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Authenticates external merchant API calls by delegating to {@link MerchantApiKeyVerifier}
 * (Member 2's work). The key itself is never logged or placed in an exception message.
 */
@Service
public class MerchantApiAuthService {

    private static final Logger log = LoggerFactory.getLogger(MerchantApiAuthService.class);
    private static final int MAX_KEY_LENGTH = 256;

    private final MerchantApiKeyVerifier verifier;

    public MerchantApiAuthService(MerchantApiKeyVerifier verifier) {
        this.verifier = verifier;
    }

    /**
     * @return the id of the merchant that owns the key
     * @throws InvalidApiKeyException if the key is missing, malformed or unknown
     */
    public Long authenticate(String apiKey) {
        if (apiKey == null || apiKey.isBlank() || apiKey.length() > MAX_KEY_LENGTH) {
            log.warn("External API request rejected: missing or malformed credentials");
            throw new InvalidApiKeyException();
        }
        return verifier.verify(apiKey.trim()).orElseThrow(() -> {
            log.warn("External API request rejected: credentials not recognised");
            return new InvalidApiKeyException();
        });
    }
}
