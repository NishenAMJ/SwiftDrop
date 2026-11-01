package com.swiftdrop.identity.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.identity.common.ApiException;
import com.swiftdrop.identity.config.IdentityProperties;

/**
 * Opaque refresh tokens with rotation and reuse detection.
 *
 * <ul>
 *   <li>Each refresh replaces the token with a new one in the same family.</li>
 *   <li>Presenting an already-replaced token means it may have been stolen, so the whole family
 *       (that login on every device it reached) is revoked.</li>
 *   <li>Exception: within a short grace period after rotation the old token is only rejected, so
 *       two tabs refreshing at the same moment don't log the user out.</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final IdentityProperties.RefreshToken settings;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, IdentityProperties properties, Clock clock) {
        this.repository = repository;
        this.settings = properties.refreshToken();
        this.clock = clock;
    }

    /** Starts a new login (new family). Returns the raw token for the cookie. */
    @Transactional
    public String create(UUID userId, String authMethod) {
        return issue(userId, UUID.randomUUID(), authMethod, clock.instant()).raw();
    }

    /**
     * Replaces a valid refresh token with a new one.
     *
     * @throws ApiException {@code REFRESH_TOKEN_INVALID} for missing, unknown, expired, revoked or
     *     reused tokens
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalid();
        }
        RefreshToken current = repository.findByTokenHash(hash(rawToken)).orElseThrow(RefreshTokenService::invalid);
        Instant now = clock.instant();

        if (current.isRevoked()) {
            if (!current.wasJustReplaced(now, settings.reuseGrace())) {
                int revoked = repository.revokeFamily(current.getFamilyId(), now);
                log.warn("Refresh token reuse for user {}: revoked {} token(s) in family {}",
                        current.getUserId(), revoked, current.getFamilyId());
            }
            throw invalid();
        }
        if (current.isExpired(now)) {
            throw invalid();
        }

        Issued next = issue(current.getUserId(), current.getFamilyId(), current.getAuthMethod(), now);
        current.replaceWith(next.token(), now);
        repository.save(current);
        return new Rotation(next.raw(), current.getUserId(), current.getAuthMethod());
    }

    /** Logout. Unknown or already revoked tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            token.revoke(clock.instant());
            repository.save(token);
        });
    }

    /** Logs the user out everywhere (for example after a password change). */
    @Transactional
    public void revokeAll(UUID userId) {
        repository.revokeAllForUser(userId, clock.instant());
    }

    private Issued issue(UUID userId, UUID familyId, String authMethod, Instant now) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken token = new RefreshToken(userId, familyId, hash(raw), authMethod, now, now.plus(settings.ttl()));
        repository.save(token);
        return new Issued(raw, token);
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID",
                "Your session has expired. Please log in again.");
    }

    private record Issued(String raw, RefreshToken token) {
    }

    /** The new raw token plus who it belongs to. */
    public record Rotation(String rawToken, UUID userId, String authMethod) {
    }
}
