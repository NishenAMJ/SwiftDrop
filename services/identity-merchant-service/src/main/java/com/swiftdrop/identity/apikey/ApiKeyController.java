package com.swiftdrop.identity.apikey;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.identity.auth.TokenResponse;
import com.swiftdrop.identity.ratelimit.RateLimits;

/**
 * API keys tag of the contract, plus the key exchange. Managing keys needs a person's MERCHANT
 * token (API-key tokens are refused in SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1")
public class ApiKeyController {

    private final ApiKeyService apiKeys;
    private final RateLimits rateLimits;

    public ApiKeyController(ApiKeyService apiKeys, RateLimits rateLimits) {
        this.apiKeys = apiKeys;
        this.rateLimits = rateLimits;
    }

    @GetMapping("/merchants/me/api-keys")
    List<ApiKeyResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return apiKeys.list(userId(jwt));
    }

    @PostMapping("/merchants/me/api-keys")
    ResponseEntity<ApiKeyResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateApiKeyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .cacheControl(CacheControl.noStore())
            .body(apiKeys.create(userId(jwt), request));
    }

    @DeleteMapping("/merchants/me/api-keys/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID keyId) {
        apiKeys.revoke(userId(jwt), keyId);
    }

    @PostMapping("/auth/token")
    ResponseEntity<TokenResponse> exchange(@RequestHeader(name = "X-API-Key", required = false) String apiKey,
            HttpServletRequest http) {
        rateLimits.beforeApiKeyExchange(http.getRemoteAddr());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(apiKeys.exchange(apiKey));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
