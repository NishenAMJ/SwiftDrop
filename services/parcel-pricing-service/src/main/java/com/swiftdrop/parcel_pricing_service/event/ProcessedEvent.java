package com.swiftdrop.parcel_pricing_service.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** An incoming event that has already been applied. Saved in the same transaction as its effect. */
@Entity
@Table(name = "processed_events", schema = "parcel_pricing")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(UUID eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = OffsetDateTime.now();
    }

    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
}
