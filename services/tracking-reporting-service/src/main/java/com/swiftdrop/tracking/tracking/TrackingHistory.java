package com.swiftdrop.tracking.tracking;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.tracking.event.EventEnvelope;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tracking_history", schema = "tracking_reporting")
public class TrackingHistory {

    @Id
    @Column(name = "history_id", nullable = false, updatable = false)
    private UUID historyId;

    @Column(name = "parcel_id", nullable = false, updatable = false)
    private UUID parcelId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;

    @Column(name = "status", nullable = false, updatable = false, length = 30)
    private String status;

    @Column(name = "public_description", nullable = false, updatable = false, length = 200)
    private String publicDescription;

    @Column(name = "safe_location_label", updatable = false, length = 120)
    private String safeLocationLabel;

    @Column(name = "event_time", nullable = false, updatable = false)
    private Instant eventTime;

    protected TrackingHistory() {
    }

    private TrackingHistory(EventEnvelope event) {
        this.historyId = UUID.randomUUID();
        this.parcelId = event.data().parcelId();
        this.eventType = event.eventType();
        this.status = event.data().deliveryStatus();
        this.publicDescription = event.data().publicDescription();
        this.safeLocationLabel = event.data().publicLocation();
        this.eventTime = event.occurredAt();
    }

    public static TrackingHistory from(EventEnvelope event) {
        return new TrackingHistory(event);
    }

    String status() {
        return status;
    }

    String publicDescription() {
        return publicDescription;
    }

    String safeLocationLabel() {
        return safeLocationLabel;
    }

    Instant eventTime() {
        return eventTime;
    }
}
