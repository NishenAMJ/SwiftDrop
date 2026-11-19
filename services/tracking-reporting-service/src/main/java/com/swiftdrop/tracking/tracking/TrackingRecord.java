package com.swiftdrop.tracking.tracking;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.tracking.common.InvalidTrackingEventException;
import com.swiftdrop.tracking.event.TrackingEventData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tracking_records", schema = "tracking_reporting")
public class TrackingRecord {

    @Id
    @Column(name = "parcel_id", nullable = false, updatable = false)
    private UUID parcelId;

    @Column(name = "tracking_number", nullable = false, unique = true, length = 32)
    private String trackingNumber;

    @Column(name = "current_status", nullable = false, length = 30)
    private String currentStatus;

    @Column(name = "latest_safe_location_label", length = 120)
    private String latestSafeLocationLabel;

    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TrackingRecord() {
    }

    private TrackingRecord(TrackingEventData data, Instant occurredAt) {
        this.parcelId = data.parcelId();
        this.trackingNumber = data.trackingNumber();
        this.currentStatus = data.deliveryStatus();
        this.latestSafeLocationLabel = data.publicLocation();
        this.lastUpdatedAt = occurredAt;
        this.createdAt = Instant.now();
    }

    public static TrackingRecord fromFirstEvent(TrackingEventData data, Instant occurredAt) {
        return new TrackingRecord(data, occurredAt);
    }

    public boolean apply(TrackingEventData data, Instant occurredAt) {
        if (!parcelId.equals(data.parcelId()) || !trackingNumber.equals(data.trackingNumber())) {
            throw new InvalidTrackingEventException("Parcel and tracking number do not match the existing record");
        }
        if (occurredAt.isBefore(lastUpdatedAt)) {
            return false;
        }
        currentStatus = data.deliveryStatus();
        if (data.publicLocation() != null) {
            latestSafeLocationLabel = data.publicLocation();
        }
        lastUpdatedAt = occurredAt;
        return true;
    }

    public TrackingState toState() {
        return new TrackingState(trackingNumber, currentStatus, latestSafeLocationLabel, lastUpdatedAt);
    }

    UUID parcelId() {
        return parcelId;
    }

    String trackingNumber() {
        return trackingNumber;
    }

    String currentStatus() {
        return currentStatus;
    }

    String latestSafeLocationLabel() {
        return latestSafeLocationLabel;
    }

    Instant lastUpdatedAt() {
        return lastUpdatedAt;
    }
}
