package com.swiftdrop.communication.webhook;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftdrop.communication.common.ApiException;
import com.swiftdrop.communication.config.WebhookProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * SSRF protections for merchant webhook URLs, using IP literals so no DNS lookup is needed.
 * Known gap (not covered by the validator today, see the test report): IPv6 unique-local
 * fc00::/7, carrier-grade NAT 100.64.0.0/10 and multicast ranges.
 */
class UrlSafetySsrfTest {

    private static UrlSafetyValidator strict() {
        WebhookProperties properties = new WebhookProperties();
        properties.setAllowHttp(false);
        properties.setAllowPrivateAddresses(false);
        return new UrlSafetyValidator(properties);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://169.254.169.254/latest/meta-data",
            "https://10.0.0.1/hook",
            "https://172.16.0.1/hook",
            "https://192.168.1.1/hook",
            "https://0.0.0.0/hook",
            "https://[::1]/hook",
            "https://[::ffff:10.0.0.1]/hook",
            "https://127.1/hook",
            "https://example.com@127.0.0.1/hook"})
    void privateLoopbackAndMetadataAddressesAreRejected(String url) {
        assertThatThrownBy(() -> strict().validate(url)).isInstanceOf(ApiException.class);
    }

    @Test
    void urlWithoutAHostIsRejected() {
        assertThatThrownBy(() -> strict().validate("https:///hook")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> strict().validateUrl("mailto:ops@example.com")).isInstanceOf(ApiException.class);
    }

    @Test
    void unresolvableHostIsRejected() {
        assertThatThrownBy(() -> strict().validate("https://does-not-exist.invalid/hook")).isInstanceOf(ApiException.class);
    }
}
