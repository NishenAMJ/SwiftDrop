package com.swiftdrop.communication.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.swiftdrop.communication.config.WebhookProperties;

/** Unit tests for {@link RetryPolicy}, the schedule the dispatcher uses after a failed attempt. */
class RetryScheduleTest {

    private static final List<Duration> DEFAULT_DELAYS = new WebhookProperties().getRetryDelays();

    @Test
    void defaultScheduleIs10s30s1m2m5m() {
        assertThat(DEFAULT_DELAYS).containsExactly(
                Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(1),
                Duration.ofMinutes(2), Duration.ofMinutes(5));
    }

    @Test
    void eachFailedAttemptWaitsForTheMatchingDelay() {
        assertThat(RetryPolicy.nextDelay(1, DEFAULT_DELAYS)).contains(Duration.ofSeconds(10));
        assertThat(RetryPolicy.nextDelay(2, DEFAULT_DELAYS)).contains(Duration.ofSeconds(30));
        assertThat(RetryPolicy.nextDelay(3, DEFAULT_DELAYS)).contains(Duration.ofMinutes(1));
        assertThat(RetryPolicy.nextDelay(4, DEFAULT_DELAYS)).contains(Duration.ofMinutes(2));
        assertThat(RetryPolicy.nextDelay(5, DEFAULT_DELAYS)).contains(Duration.ofMinutes(5));
    }

    @Test
    void afterTheLastDelayTheDeliveryGivesUp() {
        // 6th failed attempt: no delay left, so the dispatcher marks the delivery FAILED.
        assertThat(RetryPolicy.nextDelay(6, DEFAULT_DELAYS)).isEmpty();
        assertThat(RetryPolicy.nextDelay(7, DEFAULT_DELAYS)).isEmpty();
    }

    @Test
    void totalAttemptsIsFirstAttemptPlusOnePerDelay() {
        assertThat(RetryPolicy.maxAttempts(DEFAULT_DELAYS)).isEqualTo(6);
        assertThat(RetryPolicy.maxAttempts(List.of())).isEqualTo(1);
    }

    @Test
    void invalidAttemptNumberHasNoDelay() {
        assertThat(RetryPolicy.nextDelay(0, DEFAULT_DELAYS)).isEmpty();
        assertThat(RetryPolicy.nextDelay(-1, DEFAULT_DELAYS)).isEmpty();
    }

    @Test
    void noRetriesConfiguredMeansFailAfterFirstAttempt() {
        assertThat(RetryPolicy.nextDelay(1, List.of())).isEmpty();
    }
}
