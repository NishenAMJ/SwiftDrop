package com.swiftdrop.communication.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.swiftdrop.communication.config.WebhookProperties;

class WebhookSecretCryptoTest {

    private static final String VALID_KEY_BASE64 = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptAndDecryptRoundTrip() {
        WebhookProperties props = new WebhookProperties();
        props.setEncryptionKey(VALID_KEY_BASE64);

        WebhookSecretCrypto crypto = new WebhookSecretCrypto(props);
        crypto.validateKey();

        String originalSecret = "whsec_test_secret_1234567890abcdef";
        String ciphertext = crypto.encrypt(originalSecret);

        assertThat(ciphertext).isNotBlank();
        assertThat(ciphertext).isNotEqualTo(originalSecret);

        String decrypted = crypto.decrypt(ciphertext);
        assertThat(decrypted).isEqualTo(originalSecret);
    }

    @Test
    void initFailsFastWhenKeyMissing() {
        WebhookProperties props = new WebhookProperties();
        props.setEncryptionKey(null);

        WebhookSecretCrypto crypto = new WebhookSecretCrypto(props);
        assertThatThrownBy(crypto::validateKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WEBHOOK_ENCRYPTION_KEY must be a base64-encoded 32-byte key");
    }

    @Test
    void initFailsFastWhenKeyNotBase64() {
        WebhookProperties props = new WebhookProperties();
        props.setEncryptionKey("not-valid-base64!@#$");

        WebhookSecretCrypto crypto = new WebhookSecretCrypto(props);
        assertThatThrownBy(crypto::validateKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WEBHOOK_ENCRYPTION_KEY must be a base64-encoded 32-byte key");
    }

    @Test
    void initFailsFastWhenKeyNot32Bytes() {
        WebhookProperties props = new WebhookProperties();
        // 16 bytes instead of 32
        props.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[16]));

        WebhookSecretCrypto crypto = new WebhookSecretCrypto(props);
        assertThatThrownBy(crypto::validateKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WEBHOOK_ENCRYPTION_KEY must be a base64-encoded 32-byte key");
    }

    @Test
    void decryptFailsWhenCiphertextTampered() {
        WebhookProperties props = new WebhookProperties();
        props.setEncryptionKey(VALID_KEY_BASE64);

        WebhookSecretCrypto crypto = new WebhookSecretCrypto(props);
        crypto.validateKey();

        String ciphertext = crypto.encrypt("whsec_secret");
        byte[] decoded = Base64.getDecoder().decode(ciphertext);
        decoded[decoded.length - 1] ^= (byte) 0xFF; // flip bits in GCM tag
        String tampered = Base64.getEncoder().encodeToString(decoded);

        assertThatThrownBy(() -> crypto.decrypt(tampered))
                .isInstanceOf(RuntimeException.class);
    }
}
