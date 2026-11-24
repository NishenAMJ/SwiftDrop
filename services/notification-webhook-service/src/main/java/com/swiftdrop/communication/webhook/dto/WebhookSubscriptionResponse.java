package com.swiftdrop.communication.webhook.dto;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.swiftdrop.communication.webhook.WebhookSubscription;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebhookSubscriptionResponse {

    private UUID id;
    private UUID merchantId;
    private String url;
    private List<String> eventTypes;
    private boolean active;
    private String pausedReason;
    private Instant createdAt;
    private Instant updatedAt;
    private String secret;

    public WebhookSubscriptionResponse() {
    }

    public static WebhookSubscriptionResponse fromEntity(WebhookSubscription entity, String secret) {
        WebhookSubscriptionResponse response = new WebhookSubscriptionResponse();
        response.id = entity.getSubscriptionId();
        response.merchantId = entity.getMerchantId();
        response.url = entity.getUrl();
        response.eventTypes = Arrays.stream(entity.getEventTypes().split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        response.active = entity.isActive();
        response.pausedReason = entity.getPausedReason();
        response.createdAt = entity.getCreatedAt();
        response.updatedAt = entity.getUpdatedAt();
        response.secret = secret;
        return response;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getUrl() {
        return url;
    }

    public List<String> getEventTypes() {
        return eventTypes;
    }

    public boolean isActive() {
        return active;
    }

    public String getPausedReason() {
        return pausedReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getSecret() {
        return secret;
    }
}
