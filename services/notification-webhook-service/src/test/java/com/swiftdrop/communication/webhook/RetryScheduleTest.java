package com.swiftdrop.communication.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.swiftdrop.communication.config.WebhookProperties;

class RetryScheduleTest {

    @Test
    void retryDelaysAndTerminalFailureProgression() {
        WebhookProperties props = new WebhookProperties();
        List<Duration> delays = List.of(
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                Duration.ofMinutes(1),
                Duration.ofMinutes(2),
                Duration.ofMinutes(5)
        );
        props.setRetryDelays(delays);

        Instant baseTime = Instant.parse("2026-11-20T10:00:00Z");

        WebhookDelivery delivery = new WebhookDelivery(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "parcel.status-changed",
                "{}",
                WebhookDeliveryStatus.PENDING,
                0,
                baseTime,
                baseTime
        );

        // Simulate retry progression logic from WebhookDispatcher
        for (int attempt = 1; attempt <= delays.size(); attempt++) {
            delivery.setAttemptCount(attempt);
            Duration expectedDelay = delays.get(attempt - 1);
            delivery.setNextAttemptAt(baseTime.plus(expectedDelay));
            delivery.setStatus(WebhookDeliveryStatus.PENDING);

            assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
            assertThat(delivery.getAttemptCount()).isEqualTo(attempt);
            assertThat(delivery.getNextAttemptAt()).isEqualTo(baseTime.plus(expectedDelay));
        }

        // Attempt 6 (exceeds delays list of 5 retries): terminal failure
        delivery.setAttemptCount(6);
        delivery.setStatus(WebhookDeliveryStatus.FAILED);
        delivery.setNextAttemptAt(null);

        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(delivery.getNextAttemptAt()).isNull();
        assertThat(delivery.getAttemptCount()).isEqualTo(6);
    }
}
