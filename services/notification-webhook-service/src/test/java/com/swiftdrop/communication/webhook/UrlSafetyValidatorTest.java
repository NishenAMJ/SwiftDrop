package com.swiftdrop.communication.webhook;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.swiftdrop.communication.common.ApiException;
import com.swiftdrop.communication.config.WebhookProperties;

class UrlSafetyValidatorTest {

    @Test
    void allowsHttpsPublicUrl() {
        WebhookProperties props = new WebhookProperties();
        props.setAllowHttp(false);
        props.setAllowPrivateAddresses(false);

        UrlSafetyValidator validator = new UrlSafetyValidator(props);
        assertThatCode(() -> validator.validate("https://example.com/webhook"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsHttpWhenNotAllowed() {
        WebhookProperties props = new WebhookProperties();
        props.setAllowHttp(false);

        UrlSafetyValidator validator = new UrlSafetyValidator(props);
        assertThatThrownBy(() -> validator.validate("http://example.com/webhook"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("One or more fields are invalid");
    }

    @Test
    void allowsHttpWhenExplicitlyAllowed() {
        WebhookProperties props = new WebhookProperties();
        props.setAllowHttp(true);
        props.setAllowPrivateAddresses(true);

        UrlSafetyValidator validator = new UrlSafetyValidator(props);
        assertThatCode(() -> validator.validate("http://localhost:8086/dev/webhook-receiver"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsLoopbackWhenPrivateNotAllowed() {
        WebhookProperties props = new WebhookProperties();
        props.setAllowHttp(true);
        props.setAllowPrivateAddresses(false);

        UrlSafetyValidator validator = new UrlSafetyValidator(props);
        assertThatThrownBy(() -> validator.validate("http://127.0.0.1:8080/webhook"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsMalformedOrBlankOrOversizedUrl() {
        WebhookProperties props = new WebhookProperties();
        UrlSafetyValidator validator = new UrlSafetyValidator(props);

        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validate("   ")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validate("ftp://example.com/test")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validate("not-a-url")).isInstanceOf(ApiException.class);

        String tooLong = "https://example.com/" + "a".repeat(2050);
        assertThatThrownBy(() -> validator.validate(tooLong)).isInstanceOf(ApiException.class);
    }
}
