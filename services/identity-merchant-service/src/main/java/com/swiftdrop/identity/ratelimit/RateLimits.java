package com.swiftdrop.identity.ratelimit;

import java.time.Clock;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.swiftdrop.identity.config.IdentityProperties;
import com.swiftdrop.identity.user.UserService;

/**
 * The service's rate limits, named after what they protect. Limits per IP slow down one source;
 * limits per account (counting only failures) slow down guessing one person's password from many
 * sources, without locking them out for typing it right.
 *
 * <p>IP addresses come from {@code request.getRemoteAddr()}. Behind the API gateway that is the
 * gateway's address unless forwarded headers are enabled; see the service README.
 */
@Component
public class RateLimits {

    private final RateLimiter limiter;
    private final IdentityProperties.RateLimit settings;

    public RateLimits(IdentityProperties properties, Clock clock) {
        this.settings = properties.rateLimit();
        this.limiter = new RateLimiter(clock);
    }

    public void beforeRegister(String ip) {
        acquire("register-ip:" + ip, settings.registerPerIp());
    }

    /** Per-IP attempts, then whether this account has had too many failures recently. */
    public void beforeLogin(String ip, String email) {
        acquire("login-ip:" + ip, settings.loginPerIp());
        check(loginAccountKey(email), settings.loginFailuresPerAccount());
    }

    public void loginFailed(String email) {
        penalize(loginAccountKey(email), settings.loginFailuresPerAccount());
    }

    public void loginSucceeded(String email) {
        if (settings.enabled()) {
            limiter.reset(loginAccountKey(email));
        }
    }

    public void beforeRefresh(String ip) {
        acquire("refresh-ip:" + ip, settings.refreshPerIp());
    }

    public void beforeApiKeyExchange(String ip) {
        acquire("api-key-ip:" + ip, settings.apiKeyExchangePerIp());
    }

    /** Someone with a stolen access token guessing the current password. */
    public void beforePasswordChange(UUID userId) {
        check("password-user:" + userId, settings.passwordFailuresPerUser());
    }

    public void passwordChangeFailed(UUID userId) {
        penalize("password-user:" + userId, settings.passwordFailuresPerUser());
    }

    @Scheduled(fixedDelayString = "PT5M")
    void evictIdle() {
        limiter.evictIdle();
    }

    private void acquire(String key, Limit limit) {
        if (!settings.enabled()) {
            return;
        }
        long wait = limiter.tryAcquire(key, limit);
        if (wait > 0) {
            throw new RateLimitedException(wait);
        }
    }

    private void check(String key, Limit limit) {
        if (!settings.enabled()) {
            return;
        }
        long wait = limiter.waitTime(key, limit);
        if (wait > 0) {
            throw new RateLimitedException(wait);
        }
    }

    private void penalize(String key, Limit limit) {
        if (settings.enabled()) {
            limiter.tryAcquire(key, limit);
        }
    }

    private static String loginAccountKey(String email) {
        return "login-account:" + UserService.normalizeEmail(email);
    }
}
