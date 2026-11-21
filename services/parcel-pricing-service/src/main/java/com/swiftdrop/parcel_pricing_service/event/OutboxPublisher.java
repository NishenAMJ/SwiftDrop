package com.swiftdrop.parcel_pricing_service.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Sends outbox rows to RabbitMQ. A row is marked published only after the broker confirms it,
 * so a broker outage just delays events; they are retried with back-off and never dropped.
 * Messages follow the contract rules: JSON, persistent, AMQP message_id = eventId.
 */
@Component
@ConditionalOnProperty(name = "swiftdrop.events.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 20;

    private final OutboxEventRepository outbox;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate tx;

    public OutboxPublisher(OutboxEventRepository outbox, RabbitTemplate rabbit, TransactionTemplate tx) {
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.tx = tx;
        this.rabbit.setMandatory(true);
    }

    @Scheduled(fixedDelayString = "${swiftdrop.events.publisher.delay-ms:2000}")
    public void publishPending() {
        tx.executeWithoutResult(status -> {
            List<OutboxEvent> pending = outbox.lockPending(OffsetDateTime.now(), PageRequest.of(0, BATCH_SIZE));
            for (OutboxEvent event : pending) {
                try {
                    send(event);
                    event.markPublished();
                } catch (Exception ex) {
                    if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                    event.markFailed(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    log.warn("Event {} ({}) not published, retry #{} at {}", event.getId(), event.getRoutingKey(),
                            event.getAttempts(), event.getNextAttemptAt());
                    // Keep order: stop this pass, later events wait for the broker too.
                    break;
                }
            }
        });
    }

    private void send(OutboxEvent event) throws Exception {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding(StandardCharsets.UTF_8.name());
        props.setMessageId(event.getId().toString());
        props.setType(event.getRoutingKey());
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        Message message = new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), props);

        CorrelationData correlation = new CorrelationData(event.getId().toString());
        rabbit.send(event.getExchangeName(), event.getRoutingKey(), message, correlation);
        CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
        if (!confirm.isAck()) {
            throw new IllegalStateException("Broker did not confirm the message: " + confirm.getReason());
        }
        if (correlation.getReturned() != null) {
            // Nobody has bound a queue for this routing key yet. Treat as published: the contract
            // says consumers declare their own queues, and re-sending would never succeed.
            log.info("Event {} ({}) had no bound queue on {}", event.getId(), event.getRoutingKey(),
                    event.getExchangeName());
        }
    }
}
