package com.swiftdrop.parcel_pricing_service.security;

import com.swiftdrop.parcel_pricing_service.exception.ApiKeyVerificationUnavailableException;
import com.swiftdrop.parcel_pricing_service.exception.UnauthenticatedMerchantException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fail-closed fallbacks, used only when no real implementation is present. They guarantee
 * the order endpoints can never be used without authentication, even if the integration
 * beans are forgotten.
 */
@Configuration
public class MerchantSecurityDefaults {

    @Bean
    @ConditionalOnMissingBean(MerchantIdentityResolver.class)
    MerchantIdentityResolver denyAllMerchantIdentityResolver() {
        return request -> {
            throw new UnauthenticatedMerchantException();
        };
    }

    @Bean
    @ConditionalOnMissingBean(MerchantApiKeyVerifier.class)
    MerchantApiKeyVerifier unavailableMerchantApiKeyVerifier() {
        return apiKey -> {
            throw new ApiKeyVerificationUnavailableException();
        };
    }
}
