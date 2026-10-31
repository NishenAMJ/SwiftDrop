package com.swiftdrop.identity.ratelimit;

import java.time.Duration;

/**
 * At most {@code requests} per {@code per}, refilled smoothly: after using them all, one more is
 * allowed every {@code per / requests}.
 */
public record Limit(int requests, Duration per) {

    public Limit {
        if (requests < 1 || per == null || per.isNegative() || per.isZero()) {
            throw new IllegalArgumentException("A limit needs at least 1 request and a positive period");
        }
    }
}
