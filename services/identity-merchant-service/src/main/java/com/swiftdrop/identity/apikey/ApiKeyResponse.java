package com.swiftdrop.identity.apikey;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The {@code ApiKey} / {@code CreatedApiKey} schemas. {@code key} is set only in the response
 * that creates the key.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiKeyResponse(
        UUID id,
        String name,
        String prefix,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        Instant revokedAt,
        String key) {

    static ApiKeyResponse from(ApiKey k) {
        return new ApiKeyResponse(k.getId(), k.getName(), k.getPrefix(), k.getCreatedAt(), k.getLastUsedAt(),
                k.getExpiresAt(), k.getRevokedAt(), null);
    }

    static ApiKeyResponse created(ApiKey k, String fullKey) {
        return new ApiKeyResponse(k.getId(), k.getName(), k.getPrefix(), k.getCreatedAt(), null, k.getExpiresAt(),
                null, fullKey);
    }
}
