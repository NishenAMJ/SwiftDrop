package com.swiftdrop.parcel_pricing_service.security;

import com.swiftdrop.parcel_pricing_service.exception.ApiKeyVerificationUnavailableException;
import com.swiftdrop.parcel_pricing_service.exception.UnauthenticatedMerchantException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MerchantSecurityComponentsTest {

    // ---------- fail-closed defaults ----------

    @Test
    void defaultIdentityResolver_rejectsEveryRequest() {
        MerchantIdentityResolver resolver = new MerchantSecurityDefaults().denyAllMerchantIdentityResolver();

        assertThatThrownBy(() -> resolver.resolveMerchantId(new MockHttpServletRequest()))
                .isInstanceOf(UnauthenticatedMerchantException.class);
    }

    @Test
    void defaultApiKeyVerifier_failsClosed() {
        MerchantApiKeyVerifier verifier = new MerchantSecurityDefaults().unavailableMerchantApiKeyVerifier();

        assertThatThrownBy(() -> verifier.verify("any-key"))
                .isInstanceOf(ApiKeyVerificationUnavailableException.class);
    }

    // ---------- dev header resolver ----------

    @Test
    void devResolver_readsMerchantIdHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Merchant-Id", " 7 ");

        assertThat(new DevHeaderMerchantIdentityResolver().resolveMerchantId(request)).isEqualTo(7L);
    }

    @Test
    void devResolver_rejectsMissingOrInvalidHeader() {
        DevHeaderMerchantIdentityResolver resolver = new DevHeaderMerchantIdentityResolver();

        assertThatThrownBy(() -> resolver.resolveMerchantId(new MockHttpServletRequest()))
                .isInstanceOf(UnauthenticatedMerchantException.class);

        for (String bad : new String[]{"abc", "0", "-3", ""}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Merchant-Id", bad);
            assertThatThrownBy(() -> resolver.resolveMerchantId(request))
                    .isInstanceOf(UnauthenticatedMerchantException.class);
        }
    }

    // ---------- dev API key verifier ----------

    @Test
    void devVerifier_acceptsConfiguredKeysOnly() {
        DevConfigApiKeyVerifier verifier = new DevConfigApiKeyVerifier("abc123:7, def456:9");

        assertThat(verifier.verify("abc123")).contains(7L);
        assertThat(verifier.verify("def456")).contains(9L);
        assertThat(verifier.verify("abc124")).isEmpty();
        assertThat(verifier.verify("")).isEmpty();
        assertThat(verifier.verify(null)).isEmpty();
    }

    @Test
    void devVerifier_withNoConfiguredKeys_acceptsNothing() {
        assertThat(new DevConfigApiKeyVerifier("").verify("abc123")).isEmpty();
        assertThat(new DevConfigApiKeyVerifier(null).verify("abc123")).isEmpty();
    }

    @Test
    void devVerifier_malformedConfig_failsWithoutEchoingKeys() {
        assertThatThrownBy(() -> new DevConfigApiKeyVerifier("my-secret-key-without-merchant"))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("my-secret-key"));
        assertThatThrownBy(() -> new DevConfigApiKeyVerifier("key:notanumber"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DevConfigApiKeyVerifier("key:0"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
