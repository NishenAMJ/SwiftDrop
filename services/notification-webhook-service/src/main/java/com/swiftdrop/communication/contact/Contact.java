package com.swiftdrop.communication.contact;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "contacts", schema = "notification_webhook")
public class Contact {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "last_event_occurred_at", nullable = false)
    private Instant lastEventOccurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Contact() {
    }

    public Contact(UUID userId, String email, String fullName, String phoneNumber, String status,
                   Instant lastEventOccurredAt, Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.status = status;
        this.lastEventOccurredAt = lastEventOccurredAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getLastEventOccurredAt() {
        return lastEventOccurredAt;
    }

    public void setLastEventOccurredAt(Instant lastEventOccurredAt) {
        this.lastEventOccurredAt = lastEventOccurredAt;
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
}
