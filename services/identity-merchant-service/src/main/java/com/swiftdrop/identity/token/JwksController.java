package com.swiftdrop.identity.token;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public keys for verifying access tokens. Used by the gateway and every other service. */
@RestController
public class JwksController {

    private final JwtKeys keys;

    public JwksController(JwtKeys keys) {
        this.keys = keys;
    }

    @GetMapping("/.well-known/jwks.json")
    ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
            .body(keys.publicJwkSet());
    }
}
