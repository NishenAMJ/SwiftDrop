package com.swiftdrop.communication.webhook;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "webhook_deliveries", schema = "notification_webhook")
public class WebhookDelivery {

    @Id
    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WebhookDeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_status_code")
    private Integer lastStatusCode;

    @Column(name = "last_error", length = 200)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    protected WebhookDelivery() {
    }

    public WebhookDelivery(UUID deliveryId, UUID subscriptionId, UUID eventId, String eventType,
                           String payload, WebhookDeliveryStatus status, int attemptCount,
                           Instant nextAttemptAt, Instant createdAt) {
        this(deliveryId, subscriptionId, eventId, eventType, payload, status, attemptCount, nextAttemptAt, null, null, createdAt, null);
    }

    public WebhookDelivery(UUID deliveryId, UUID subscriptionId, UUID eventId, String eventType,
                           String payload, WebhookDeliveryStatus status, int attemptCount,
                           Instant nextAttemptAt, Integer lastStatusCode, String lastError,
                           Instant createdAt, Instant deliveredAt) {
        this.deliveryId = deliveryId;
        this.subscriptionId = subscriptionId;
        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt;
        this.lastStatusCode = lastStatusCode;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.deliveredAt = deliveredAt;
    }

    public UUID getDeliveryId() {
        return deliveryId;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public WebhookDeliveryStatus getStatus() {
        return status;
    }

    public void setStatus(WebhookDeliveryStatus status) {
        this.status = status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public Integer getLastStatusCode() {
        return lastStatusCode;
    }

    public void setLastStatusCode(Integer lastStatusCode) {
        this.lastStatusCode = lastStatusCode;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }
}
