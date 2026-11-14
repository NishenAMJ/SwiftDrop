package com.swiftdrop.communication.notification;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications", schema = "notification_webhook")
public class Notification {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "template", nullable = false, length = 100)
    private String template;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private NotificationStatus status;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(UUID id, UUID recipientUserId, String channel, String template,
                        NotificationStatus status, String failureReason, Instant createdAt) {
        this.id = id;
        this.recipientUserId = recipientUserId;
        this.channel = channel;
        this.template = template;
        this.status = status;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRecipientUserId() {
        return recipientUserId;
    }

    public String getChannel() {
        return channel;
    }

    public String getTemplate() {
        return template;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
