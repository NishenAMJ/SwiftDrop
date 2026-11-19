package com.swiftdrop.communication.webhook;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.swiftdrop.communication.config.WebhookProperties;

import jakarta.annotation.PostConstruct;

@Component
public class WebhookSecretCrypto {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String KEY_GENERATION_CMD =
            "$bytes = [byte[]]::new(32); [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes); [Convert]::ToBase64String($bytes)";

    private final WebhookProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKey secretKey;

    public WebhookSecretCrypto(WebhookProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void validateKey() {
        String key = properties.getEncryptionKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("WEBHOOK_ENCRYPTION_KEY must be a base64-encoded 32-byte key. Generate one with: "
                    + KEY_GENERATION_CMD);
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("WEBHOOK_ENCRYPTION_KEY must be a base64-encoded 32-byte key. Generate one with: "
                    + KEY_GENERATION_CMD);
        }

        if (decoded.length != 32) {
            throw new IllegalStateException("WEBHOOK_ENCRYPTION_KEY must be a base64-encoded 32-byte key. Generate one with: "
                    + KEY_GENERATION_CMD);
        }

        this.secretKey = new SecretKeySpec(decoded, ALGORITHM);
    }

    public String generateSecret() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String encrypt(String rawSecret) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(rawSecret.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt webhook secret", e);
        }
    }

    public String decrypt(String ciphertextBase64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertextBase64);
            if (decoded.length < GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted payload");
            }

            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);

            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt webhook secret", e);
        }
    }
}
