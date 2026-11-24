package com.swiftdrop.communication.webhook;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "webhook_subscriptions", schema = "notification_webhook")
public class WebhookSubscription {

    @Id
    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Column(name = "event_types", nullable = false, length = 500)
    private String eventTypes;

    @Column(name = "secret_ciphertext", nullable = false, columnDefinition = "TEXT")
    private String secretCiphertext;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "paused_reason", length = 100)
    private String pausedReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    protected WebhookSubscription() {
    }

    public WebhookSubscription(UUID subscriptionId, UUID merchantId, String url, String eventTypes,
                               String secretCiphertext, boolean active, String pausedReason,
                               Instant createdAt, Instant updatedAt) {
        this.subscriptionId = subscriptionId;
        this.merchantId = merchantId;
        this.url = url;
        this.eventTypes = eventTypes;
        this.secretCiphertext = secretCiphertext;
        this.active = active;
        this.pausedReason = pausedReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(String eventTypes) {
        this.eventTypes = eventTypes;
    }

    public String getSecretCiphertext() {
        return secretCiphertext;
    }

    public void setSecretCiphertext(String secretCiphertext) {
        this.secretCiphertext = secretCiphertext;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getPausedReason() {
        return pausedReason;
    }

    public void setPausedReason(String pausedReason) {
        this.pausedReason = pausedReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
