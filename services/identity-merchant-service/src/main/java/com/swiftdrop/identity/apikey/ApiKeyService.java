package com.swiftdrop.identity.apikey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.identity.auth.TokenResponse;
import com.swiftdrop.identity.common.ApiException;
import com.swiftdrop.identity.merchant.Merchant;
import com.swiftdrop.identity.merchant.MerchantRepository;
import com.swiftdrop.identity.token.AccessTokenService;
import com.swiftdrop.identity.token.AccessTokenService.IssuedAccessToken;
import com.swiftdrop.identity.user.User;
import com.swiftdrop.identity.user.UserService;

/**
 * Merchant API keys: {@code sd_live_} followed by 32 random characters. Only a SHA-256 hash is
 * stored, so a database leak doesn't leak usable keys. Keys work only while the merchant is
 * APPROVED, so suspending a merchant stops its keys at once.
 */
@Service
public class ApiKeyService {

    static final int MAX_ACTIVE_KEYS = 5;
    static final String KEY_PREFIX = "sd_live_";
    private static final int DISPLAY_PREFIX_LENGTH = KEY_PREFIX.length() + 4;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository keys;
    private final MerchantRepository merchants;
    private final UserService userService;
    private final AccessTokenService accessTokens;
    private final Clock clock;

    public ApiKeyService(ApiKeyRepository keys, MerchantRepository merchants, UserService userService,
            AccessTokenService accessTokens, Clock clock) {
        this.keys = keys;
        this.merchants = merchants;
        this.userService = userService;
        this.accessTokens = accessTokens;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list(UUID ownerUserId) {
        Merchant merchant = merchantOf(ownerUserId);
        return keys.findByMerchantIdOrderByCreatedAtDesc(merchant.getId()).stream().map(ApiKeyResponse::from).toList();
    }

    @Transactional
    public ApiKeyResponse create(UUID ownerUserId, CreateApiKeyRequest request) {
        // Lock the merchant so two requests at once can't both pass the limit check.
        Merchant merchant = merchants.lockByUserId(ownerUserId).orElseThrow(ApiKeyService::noMerchant);
        requireApproved(merchant);
        Instant now = clock.instant();
        if (keys.countActive(merchant.getId(), now) >= MAX_ACTIVE_KEYS) {
            throw new ApiException(HttpStatus.CONFLICT, "API_KEY_LIMIT_REACHED",
                    "You already have " + MAX_ACTIVE_KEYS + " active API keys. Revoke one first.");
        }

        String fullKey = KEY_PREFIX + randomPart();
        ApiKey key = new ApiKey(merchant.getId(), request.name().trim(), fullKey.substring(0, DISPLAY_PREFIX_LENGTH),
                hash(fullKey), request.expiresAt(), now);
        keys.save(key);
        return ApiKeyResponse.created(key, fullKey);
    }

    @Transactional
    public void revoke(UUID ownerUserId, UUID keyId) {
        Merchant merchant = merchantOf(ownerUserId);
        ApiKey key = keys.findByIdAndMerchantId(keyId, merchant.getId())
            .orElseThrow(() -> ApiException.notFound("API key not found."));
        key.revoke(clock.instant());
        keys.save(key);
    }

    /**
     * {@code POST /api/v1/auth/token}: a valid key of an approved merchant becomes a short-lived
     * access token. The key must still be checked on every exchange, because it can be revoked or
     * the merchant suspended at any time.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse exchange(String fullKey) {
        if (fullKey == null || !fullKey.startsWith(KEY_PREFIX)) {
            throw invalidKey();
        }
        Instant now = clock.instant();
        ApiKey key = keys.findByKeyHash(hash(fullKey)).orElseThrow(ApiKeyService::invalidKey);
        if (!key.isUsable(now)) {
            throw invalidKey();
        }
        Merchant merchant = merchants.findById(key.getMerchantId()).orElseThrow(ApiKeyService::invalidKey);
        User owner = userService.get(merchant.getUserId());
        if (!merchant.isApproved() || !owner.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MERCHANT_NOT_ACTIVE",
                    "This merchant account is not active.");
        }
        key.markUsed(now);
        keys.save(key);

        IssuedAccessToken token = accessTokens.issueForApiKey(owner.getId(), merchant.getId());
        return new TokenResponse(token.value(), "Bearer", token.expiresInSeconds(), null);
    }

    private Merchant merchantOf(UUID ownerUserId) {
        return merchants.findByUserId(ownerUserId).orElseThrow(ApiKeyService::noMerchant);
    }

    /** The token says MERCHANT, but it can be up to 15 minutes old. */
    private static void requireApproved(Merchant merchant) {
        if (!merchant.isApproved()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MERCHANT_NOT_ACTIVE", "This merchant account is not active.");
        }
    }

    private static String randomPart() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String fullKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(fullKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static ApiException noMerchant() {
        return new ApiException(HttpStatus.FORBIDDEN, "MERCHANT_NOT_ACTIVE", "You are not an approved merchant.");
    }

    private static ApiException invalidKey() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "API_KEY_INVALID", "The API key is invalid, revoked or expired.");
    }
}
