package com.swiftdrop.identity.ratelimit;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token buckets, one per key. Thread-safe: each key is updated atomically.
 *
 * <p>Counts are per service instance and are lost on restart. That is enough to stop password
 * guessing against one instance; a shared store (for example Redis) would be needed for exact
 * limits across several instances.
 */
public class RateLimiter {

    /** Above this many tracked keys, idle ones are cleared at once, to bound memory. */
    static final int MAX_KEYS = 100_000;

    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Uses one request from the bucket if there is one.
     *
     * @return 0 if allowed, otherwise milliseconds until the next request would be allowed
     */
    public long tryAcquire(String key, Limit limit) {
        long now = clock.millis();
        long[] waitMillis = {0};
        buckets.compute(key, (k, bucket) -> {
            double tokens = available(bucket, limit, now);
            if (tokens >= 1) {
                return new Bucket(tokens - 1, now, limit.per().toMillis());
            }
            waitMillis[0] = millisUntilOne(tokens, limit);
            return new Bucket(tokens, now, limit.per().toMillis());
        });
        trimIfLarge();
        return waitMillis[0];
    }

    /**
     * Checks without using anything.
     *
     * @return 0 if a request would be allowed now, otherwise milliseconds to wait
     */
    public long waitTime(String key, Limit limit) {
        Bucket bucket = buckets.get(key);
        double tokens = available(bucket, limit, clock.millis());
        return tokens >= 1 ? 0 : millisUntilOne(tokens, limit);
    }

    /** Forgets a key (for example failed logins after a successful one). */
    public void reset(String key) {
        buckets.remove(key);
    }

    /** Removes buckets that have refilled completely; they behave exactly like new ones. */
    public void evictIdle() {
        long now = clock.millis();
        buckets.entrySet().removeIf(e -> now - e.getValue().updatedAt() >= e.getValue().windowMillis());
    }

    int size() {
        return buckets.size();
    }

    private void trimIfLarge() {
        if (buckets.size() > MAX_KEYS) {
            evictIdle();
        }
    }

    private static double available(Bucket bucket, Limit limit, long now) {
        if (bucket == null) {
            return limit.requests();
        }
        double refill = (double) (now - bucket.updatedAt()) * limit.requests() / limit.per().toMillis();
        return Math.min(limit.requests(), bucket.tokens() + refill);
    }

    private static long millisUntilOne(double tokens, Limit limit) {
        return (long) Math.ceil((1 - tokens) * limit.per().toMillis() / limit.requests());
    }

    private record Bucket(double tokens, long updatedAt, long windowMillis) {
    }
}
