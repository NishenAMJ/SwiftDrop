package com.swiftdrop.communication.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

@Component
public class WebhookSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    public String computeSignature(String rawSecret, long timestampSeconds, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(rawSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            String data = timestampSeconds + "." + payload;
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256 signature", e);
        }
    }

    public String buildHeader(String rawSecret, long timestampSeconds, String payload) {
        String signature = computeSignature(rawSecret, timestampSeconds, payload);
        return "t=" + timestampSeconds + ",v1=" + signature;
    }

    public boolean verify(String rawSecret, String signatureHeader, String payload, long maxAgeSeconds) {
        if (signatureHeader == null || !signatureHeader.contains("t=") || !signatureHeader.contains("v1=")) {
            return false;
        }

        try {
            long timestamp = -1;
            String signature = null;

            for (String part : signatureHeader.split(",")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    if ("t".equals(kv[0].trim())) {
                        timestamp = Long.parseLong(kv[1].trim());
                    } else if ("v1".equals(kv[0].trim())) {
                        signature = kv[1].trim();
                    }
                }
            }

            if (timestamp <= 0 || signature == null) {
                return false;
            }

            long nowSeconds = System.currentTimeMillis() / 1000;
            if (maxAgeSeconds > 0 && Math.abs(nowSeconds - timestamp) > maxAgeSeconds) {
                return false;
            }

            String expectedSignature = computeSignature(rawSecret, timestamp, payload);
            return MessageDigest.isEqual(
                    signature.getBytes(StandardCharsets.UTF_8),
                    expectedSignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }
}
