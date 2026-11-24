package com.swiftdrop.communication.webhook;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Decides when a failed webhook delivery is tried again.
 *
 * <p>The first attempt is immediate. After the Nth failed attempt the delivery waits
 * {@code retryDelays[N-1]} and is tried again. When no delay is left, it gives up (FAILED).
 * With the default 5 delays that is 6 attempts in total.
 *
 * <p>Kept separate from {@link WebhookDispatcher} so the schedule can be unit-tested without
 * HTTP or a database.
 */
public final class RetryPolicy {

    private RetryPolicy() {
    }

    /**
     * @param failedAttempts how many attempts have failed so far (1 after the first failure)
     * @return the wait before the next attempt, or empty when the delivery should be marked FAILED
     */
    public static Optional<Duration> nextDelay(int failedAttempts, List<Duration> retryDelays) {
        if (failedAttempts < 1 || failedAttempts > retryDelays.size()) {
            return Optional.empty();
        }
        return Optional.of(retryDelays.get(failedAttempts - 1));
    }

    /** Total attempts before giving up: the first one plus one per retry delay. */
    public static int maxAttempts(List<Duration> retryDelays) {
        return retryDelays.size() + 1;
    }
}
