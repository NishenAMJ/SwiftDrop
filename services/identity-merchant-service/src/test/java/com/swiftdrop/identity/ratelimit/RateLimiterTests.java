package com.swiftdrop.identity.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class RateLimiterTests {

    private final MutableClock clock = new MutableClock();
    private final RateLimiter limiter = new RateLimiter(clock);
    private final Limit threePerMinute = new Limit(3, Duration.ofMinutes(1));

    @Test
    void allowsTheLimitThenReportsHowLongToWait() {
        assertThat(limiter.tryAcquire("k", threePerMinute)).isZero();
        assertThat(limiter.tryAcquire("k", threePerMinute)).isZero();
        assertThat(limiter.tryAcquire("k", threePerMinute)).isZero();

        // One request comes back every 20 seconds.
        assertThat(limiter.tryAcquire("k", threePerMinute)).isEqualTo(20_000);
        clock.advance(Duration.ofSeconds(15));
        assertThat(limiter.waitTime("k", threePerMinute)).isEqualTo(5_000);
        clock.advance(Duration.ofSeconds(5));
        assertThat(limiter.tryAcquire("k", threePerMinute)).isZero();
        assertThat(limiter.tryAcquire("k", threePerMinute)).isPositive();
    }

    @Test
    void keysAreIndependentAndResetClearsOne() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a", threePerMinute);
        }
        assertThat(limiter.tryAcquire("a", threePerMinute)).isPositive();
        assertThat(limiter.tryAcquire("b", threePerMinute)).isZero();

        limiter.reset("a");
        assertThat(limiter.tryAcquire("a", threePerMinute)).isZero();
    }

    @Test
    void waitTimeDoesNotUseRequests() {
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.waitTime("k", threePerMinute)).isZero();
        }
        assertThat(limiter.tryAcquire("k", threePerMinute)).isZero();
    }

    @Test
    void neverRefillsAboveTheLimit() {
        limiter.tryAcquire("k", threePerMinute);
        clock.advance(Duration.ofHours(5));
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("k", threePerMinute)).isZero();
        }
        assertThat(limiter.tryAcquire("k", threePerMinute)).isPositive();
    }

    @Test
    void idleBucketsAreEvictedOnceFull() {
        limiter.tryAcquire("old", threePerMinute);
        clock.advance(Duration.ofSeconds(30));
        limiter.tryAcquire("recent", threePerMinute);
        clock.advance(Duration.ofSeconds(30));

        limiter.evictIdle();

        assertThat(limiter.size()).isEqualTo(1);
    }

    @Test
    void rejectsNonsenseLimits() {
        assertThatThrownBy(() -> new Limit(0, Duration.ofMinutes(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Limit(1, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retryAfterIsRoundedUpToWholeSeconds() {
        assertThat(new RateLimitedException(1).retryAfterSeconds()).isEqualTo(1);
        assertThat(new RateLimitedException(20_000).retryAfterSeconds()).isEqualTo(20);
        assertThat(new RateLimitedException(20_001).retryAfterSeconds()).isEqualTo(21);
        assertThat(new RateLimitedException(20_000).getMessage()).isEqualTo("Too many attempts. Try again in 20 seconds.");
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
