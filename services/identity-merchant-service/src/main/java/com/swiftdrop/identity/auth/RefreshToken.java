package com.swiftdrop.identity.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One refresh token. Only its SHA-256 hash is stored. See V3__create_refresh_tokens.sql. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @Column(name = "refresh_token_id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** All tokens from one login share a family; reuse of an old one revokes the family. */
    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    /** How the login started ({@code password} or {@code google}); copied into access tokens. */
    @Column(name = "auth_method", nullable = false, length = 20)
    private String authMethod;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    protected RefreshToken() {
    }

    RefreshToken(UUID userId, UUID familyId, String tokenHash, String authMethod, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.authMethod = authMethod;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    boolean isRevoked() {
        return revokedAt != null;
    }

    boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    /** True if this token was rotated (not logged out) less than {@code grace} ago. */
    boolean wasJustReplaced(Instant now, Duration grace) {
        return replacedBy != null && revokedAt != null && revokedAt.plus(grace).isAfter(now);
    }

    void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    void replaceWith(RefreshToken next, Instant now) {
        revoke(now);
        replacedBy = next.id;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    UUID getFamilyId() {
        return familyId;
    }

    String getAuthMethod() {
        return authMethod;
    }
}
