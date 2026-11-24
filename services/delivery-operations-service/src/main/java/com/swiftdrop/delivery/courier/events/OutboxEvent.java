package com.swiftdrop.delivery.courier.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** An event waiting to be published. The id is also the event's eventId and AMQP message id. */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    /** The exchange this event is published to. */
    @Column(nullable = false, length = 100)
    private String exchange;

    @Column(name = "routing_key", nullable = false, length = 100)
    private String routingKey;

    @Column(nullable = false, length = 4000)
    private String payload;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, String exchange, String routingKey, String payload, Instant createdAt) {
        this.id = id;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.payload = payload;
        this.createdAt = createdAt;
        this.nextAttemptAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getExchange() { return exchange; }
    public String getRoutingKey() { return routingKey; }
    public String getPayload() { return payload; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
