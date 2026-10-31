package com.swiftdrop.identity.apikey;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A merchant API key. Only the SHA-256 hash of the key is stored. See V6__create_api_keys.sql. */
@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @Column(name = "api_key_id")
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "prefix", nullable = false, length = 20)
    private String prefix;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ApiKey() {
    }

    ApiKey(UUID merchantId, String name, String prefix, String keyHash, Instant expiresAt, Instant now) {
        this.id = UUID.randomUUID();
        this.merchantId = merchantId;
        this.name = name;
        this.prefix = prefix;
        this.keyHash = keyHash;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    boolean isUsable(Instant now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }

    void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    void markUsed(Instant now) {
        lastUsedAt = now;
    }

    UUID getId() {
        return id;
    }

    UUID getMerchantId() {
        return merchantId;
    }

    String getName() {
        return name;
    }

    String getPrefix() {
        return prefix;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getLastUsedAt() {
        return lastUsedAt;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }
}
