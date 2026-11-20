package com.swiftdrop.communication.webhook.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.swiftdrop.communication.webhook.WebhookDelivery;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebhookDeliveryResponse {

    private UUID id;
    private UUID subscriptionId;
    private UUID eventId;
    private String eventType;
    private String payload;
    private String status;
    private int attemptCount;
    private Instant nextAttemptAt;
    private Integer lastStatusCode;
    private String lastError;
    private Instant createdAt;
    private Instant deliveredAt;

    public WebhookDeliveryResponse() {
    }

    public static WebhookDeliveryResponse fromEntity(WebhookDelivery entity, boolean includePayload) {
        WebhookDeliveryResponse response = new WebhookDeliveryResponse();
        response.id = entity.getDeliveryId();
        response.subscriptionId = entity.getSubscriptionId();
        response.eventId = entity.getEventId();
        response.eventType = entity.getEventType();
        response.payload = includePayload ? entity.getPayload() : null;
        response.status = entity.getStatus().name();
        response.attemptCount = entity.getAttemptCount();
        response.nextAttemptAt = entity.getNextAttemptAt();
        response.lastStatusCode = entity.getLastStatusCode();
        response.lastError = entity.getLastError();
        response.createdAt = entity.getCreatedAt();
        response.deliveredAt = entity.getDeliveredAt();
        return response;
    }

    public UUID getId() {
        return id;
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

    public String getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Integer getLastStatusCode() {
        return lastStatusCode;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }
}
