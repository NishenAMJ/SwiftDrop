package com.swiftdrop.identity.event;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.swiftdrop.identity.config.IdentityProperties;

/**
 * Sends recorded outbox events to the {@code swiftdrop.identity} exchange, oldest first, with the
 * event type as routing key (contracts/README.md, "Event conventions").
 *
 * <p>An event is marked published only after RabbitMQ confirms it, so delivery is at least once:
 * a crash between the confirm and the commit sends it again, and consumers skip repeats by
 * {@code eventId}. If RabbitMQ is unreachable, events wait in the table and the publisher retries
 * with a growing delay. Logins and registrations never wait for the broker.
 */
@Component
public class OutboxPublisher {

    public static final String EXCHANGE = "swiftdrop.identity";

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(2);

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final IdentityProperties.Outbox settings;

    private int consecutiveFailures;
    private Instant nextAttemptAt = Instant.MIN;

    public OutboxPublisher(OutboxEventRepository repository, RabbitTemplate rabbit,
            TransactionTemplate transaction, Clock clock, IdentityProperties properties) {
        this.repository = repository;
        this.rabbit = rabbit;
        this.transaction = transaction;
        this.clock = clock;
        this.settings = properties.outbox();
    }

    @Scheduled(fixedDelayString = "PT1S")
    void publishOnSchedule() {
        if (!settings.publishEnabled() || clock.instant().isBefore(nextAttemptAt)) {
            return;
        }
        Result result = publishPending();
        if (result.failed()) {
            consecutiveFailures++;
            Duration delay = retryDelay(consecutiveFailures);
            nextAttemptAt = clock.instant().plus(delay);
            // One line per failed attempt, never the payload: it holds names and contact details.
            log.warn("Could not publish identity events to RabbitMQ ({}); {} sent before the failure, retrying in {}s",
                    result.failure(), result.published(), delay.toSeconds());
        } else {
            if (consecutiveFailures > 0) {
                log.info("Publishing identity events to RabbitMQ again");
            }
            consecutiveFailures = 0;
        }
    }

    /**
     * Sends up to one batch of waiting events. Stops at the first failure so events stay in order.
     */
    public Result publishPending() {
        return transaction.execute(status -> {
            List<OutboxEvent> batch = repository.lockOldestUnpublished(settings.batchSize());
            int published = 0;
            for (OutboxEvent event : batch) {
                String failure = send(event);
                if (failure != null) {
                    return new Result(published, failure);
                }
                event.markPublished(clock.instant());
                published++;
            }
            return new Result(published, null);
        });
    }

    /** Returns null when RabbitMQ confirmed the message, otherwise a short reason without event data. */
    private String send(OutboxEvent event) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setMessageId(event.getId().toString());
        properties.setType(event.getEventType());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        Message message = new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), properties);
        CorrelationData correlation = new CorrelationData(event.getId().toString());
        try {
            rabbit.send(EXCHANGE, event.getEventType(), message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return confirm.isAck() ? null : "broker rejected event " + event.getId();
        } catch (AmqpException ex) {
            return ex.getClass().getSimpleName();
        } catch (TimeoutException ex) {
            return "no confirm within " + CONFIRM_TIMEOUT.toSeconds() + "s";
        } catch (ExecutionException ex) {
            return "confirm failed";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
    }

    private Duration retryDelay(int failures) {
        Duration delay = FIRST_RETRY_DELAY.multipliedBy(1L << Math.min(failures - 1, 10));
        return delay.compareTo(settings.maxRetryDelay()) > 0 ? settings.maxRetryDelay() : delay;
    }

    /** How many events were sent, and why sending stopped early ({@code null} if it didn't). */
    public record Result(int published, String failure) {
        public boolean failed() {
            return failure != null;
        }
    }
}
