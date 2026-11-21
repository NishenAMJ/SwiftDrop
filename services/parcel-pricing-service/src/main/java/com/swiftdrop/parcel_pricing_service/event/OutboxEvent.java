package com.swiftdrop.parcel_pricing_service.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One RabbitMQ message waiting to be published. The id is also the envelope eventId. */
@Entity
@Table(name = "event_outbox", schema = "parcel_pricing")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "exchange_name", nullable = false, length = 100)
    private String exchangeName;

    @Column(name = "routing_key", nullable = false, length = 100)
    private String routingKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, String exchangeName, String routingKey, String payload) {
        this.id = id;
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
        this.payload = payload;
        this.createdAt = OffsetDateTime.now();
        this.nextAttemptAt = this.createdAt;
    }

    public void markPublished() {
        this.publishedAt = OffsetDateTime.now();
        this.lastError = null;
    }

    /** Exponential back-off capped at about four minutes, as in the payment service outbox. */
    public void markFailed(String error) {
        this.attempts++;
        long delaySeconds = Math.min(256, 1L << Math.min(attempts, 8));
        this.nextAttemptAt = OffsetDateTime.now().plusSeconds(delaySeconds);
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
    }

    public UUID getId() { return id; }
    public String getExchangeName() { return exchangeName; }
    public String getRoutingKey() { return routingKey; }
    public String getPayload() { return payload; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public int getAttempts() { return attempts; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
}
