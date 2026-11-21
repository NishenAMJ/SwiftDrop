package com.swiftdrop.delivery.courier.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Sends outbox rows to RabbitMQ. A row is marked published only after the broker confirms it, so a
 * crash or outage means a retry (at-least-once); consumers skip duplicates by eventId.
 */
@Component
@ConditionalOnProperty(name = "delivery.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 20;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final RabbitTemplate rabbit;
    private final String table;

    public OutboxPublisher(JdbcTemplate jdbc, TransactionTemplate transaction, RabbitTemplate rabbit,
                           @Value("${delivery.outbox.table:delivery_operations.outbox_events}") String table) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.rabbit = rabbit;
        this.table = table;
    }

    @Scheduled(fixedDelayString = "${delivery.outbox.delay-ms:2000}")
    public void publishPending() {
        // Rows stay locked for the whole batch, so two instances never publish the same row.
        transaction.executeWithoutResult(status -> {
            var due = jdbc.queryForList(
                    "SELECT id, routing_key, payload, attempts FROM " + table
                            + " WHERE published_at IS NULL AND next_attempt_at <= ?"
                            + " ORDER BY created_at LIMIT " + BATCH_SIZE + " FOR UPDATE SKIP LOCKED",
                    Timestamp.from(Instant.now()));
            for (var row : due) {
                publish(row.get("id"), (String) row.get("routing_key"), (String) row.get("payload"),
                        ((Number) row.get("attempts")).intValue());
            }
        });
    }

    private void publish(Object rawId, String routingKey, String payload, int attempts) {
        var id = rawId instanceof UUID uuid ? uuid : UUID.fromString(rawId.toString());
        try {
            var properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setMessageId(id.toString());
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            var correlation = new CorrelationData(id.toString());
            rabbit.send(DeliveryEventsConfig.DELIVERY_EXCHANGE, routingKey,
                    new Message(payload.getBytes(StandardCharsets.UTF_8), properties), correlation);
            var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.ack()) {
                throw new IllegalStateException("Broker did not confirm the message");
            }
            jdbc.update("UPDATE " + table + " SET published_at = ? WHERE id = ?", Timestamp.from(Instant.now()), id);
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            var next = attempts + 1;
            var delaySeconds = Math.min(300L, 1L << Math.min(next, 8));
            jdbc.update("UPDATE " + table + " SET attempts = ?, next_attempt_at = ? WHERE id = ?", next,
                    Timestamp.from(Instant.now().plusSeconds(delaySeconds)), id);
            LOG.warn("Delivery event will be retried eventId={} attempts={} reason={}", id, next,
                    failure.getMessage());
        }
    }
}
