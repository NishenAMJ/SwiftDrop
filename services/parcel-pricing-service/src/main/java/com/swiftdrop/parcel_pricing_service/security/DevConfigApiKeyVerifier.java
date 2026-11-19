package com.swiftdrop.parcel_pricing_service.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * LOCAL TESTING ONLY (profile {@code dev}), standing in until Member 2's verifier exists.
 *
 * <p>Test keys come from configuration ({@code swiftdrop.integration.dev-api-keys}, format
 * {@code key:merchantId,key2:merchantId2}, ideally supplied through an environment variable),
 * never from source code. Keys are held only as SHA-256 digests and compared in constant time.
 * This is not a key management system and does not replace Member 2's work.
 */
@Component
@Profile("dev")
public class DevConfigApiKeyVerifier implements MerchantApiKeyVerifier {

    private static final Logger log = LoggerFactory.getLogger(DevConfigApiKeyVerifier.class);

    private record Entry(byte[] digest, Long merchantId) {
    }

    private final List<Entry> entries = new ArrayList<>();

    public DevConfigApiKeyVerifier(@Value("${swiftdrop.integration.dev-api-keys:}") String config) {
        if (config != null && !config.isBlank()) {
            for (String pair : config.split(",")) {
                String trimmed = pair.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                int separator = trimmed.lastIndexOf(':');
                if (separator <= 0 || separator == trimmed.length() - 1) {
                    // Do not echo the value: it contains a key.
                    throw new IllegalArgumentException(
                            "swiftdrop.integration.dev-api-keys must look like key:merchantId,key2:merchantId2");
                }
                String key = trimmed.substring(0, separator);
                long merchantId = parseMerchantId(trimmed.substring(separator + 1));
                entries.add(new Entry(sha256(key), merchantId));
            }
        }
        log.warn("DEV API key verifier is active with {} configured test key(s). "
                + "Never use the 'dev' profile in production.", entries.size());
    }

    @Override
    public Optional<Long> verify(String apiKey) {
        if (apiKey == null) {
            return Optional.empty();
        }
        byte[] presented = sha256(apiKey);
        Long match = null;
        for (Entry entry : entries) {
            // Check every entry so timing does not reveal which one matched.
            if (MessageDigest.isEqual(entry.digest(), presented)) {
                match = entry.merchantId();
            }
        }
        return Optional.ofNullable(match);
    }

    private static long parseMerchantId(String value) {
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("swiftdrop.integration.dev-api-keys contains an invalid merchant id");
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
