package com.swiftdrop.identity.ratelimit;

import org.springframework.http.HttpStatus;

import com.swiftdrop.identity.common.ApiException;

/** 429 with a {@code Retry-After} header (added by ApiExceptionHandler). */
public class RateLimitedException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterMillis) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", message(seconds(retryAfterMillis)));
        this.retryAfterSeconds = seconds(retryAfterMillis);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    private static long seconds(long millis) {
        return Math.max(1, (millis + 999) / 1000);
    }

    private static String message(long seconds) {
        return "Too many attempts. Try again in " + seconds + (seconds == 1 ? " second." : " seconds.");
    }
}
