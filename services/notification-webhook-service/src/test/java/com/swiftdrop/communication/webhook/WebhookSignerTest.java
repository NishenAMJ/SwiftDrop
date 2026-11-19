package com.swiftdrop.communication.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebhookSignerTest {

    private final WebhookSigner signer = new WebhookSigner();
    private final String secret = "whsec_test_secret_key_1234567890";
    private final String payload = "{\"eventId\":\"11111111-1111-1111-1111-111111111111\",\"eventType\":\"parcel.status-changed\"}";

    @Test
    void signAndVerifySuccess() {
        long timestamp = System.currentTimeMillis() / 1000;
        String header = signer.buildHeader(secret, timestamp, payload);

        assertThat(header).startsWith("t=" + timestamp + ",v1=");
        boolean verified = signer.verify(secret, header, payload, 300);
        assertThat(verified).isTrue();
    }

    @Test
    void verifyFailsWhenPayloadModified() {
        long timestamp = System.currentTimeMillis() / 1000;
        String header = signer.buildHeader(secret, timestamp, payload);

        String tamperedPayload = payload.replace("11111111", "22222222");
        boolean verified = signer.verify(secret, header, tamperedPayload, 300);
        assertThat(verified).isFalse();
    }

    @Test
    void verifyFailsWhenSecretMismatch() {
        long timestamp = System.currentTimeMillis() / 1000;
        String header = signer.buildHeader(secret, timestamp, payload);

        boolean verified = signer.verify("whsec_wrong_secret", header, payload, 300);
        assertThat(verified).isFalse();
    }

    @Test
    void verifyFailsWhenTimestampExpired() {
        long oldTimestamp = (System.currentTimeMillis() / 1000) - 600; // 10 minutes ago
        String header = signer.buildHeader(secret, oldTimestamp, payload);

        boolean verified = signer.verify(secret, header, payload, 300); // tolerance 5 minutes
        assertThat(verified).isFalse();
    }

    @Test
    void verifyFailsOnMalformedHeader() {
        assertThat(signer.verify(secret, "invalid_header", payload, 300)).isFalse();
        assertThat(signer.verify(secret, null, payload, 300)).isFalse();
        assertThat(signer.verify(secret, "t=123", payload, 300)).isFalse();
        assertThat(signer.verify(secret, "v1=abc", payload, 300)).isFalse();
    }
}
