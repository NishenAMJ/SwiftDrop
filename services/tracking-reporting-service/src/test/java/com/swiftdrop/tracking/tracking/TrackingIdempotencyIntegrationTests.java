package com.swiftdrop.tracking.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.tracking.event.EventEnvelope;
import com.swiftdrop.tracking.event.TrackingEventData;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@Transactional
class TrackingIdempotencyIntegrationTests {

    @Autowired
    private TrackingProjectionUpdater updater;

    @Autowired
    private TrackingRecordRepository trackingRecords;

    @Autowired
    private TrackingHistoryRepository trackingHistory;

    @Test
    void storesOnlyOneProjectionAndHistoryEntryForADuplicateEventId() {
        UUID parcelId = UUID.randomUUID();
        EventEnvelope event = parcelBookedEvent(parcelId);

        TrackingProjectionUpdater.Result first = updater.process(event);
        TrackingProjectionUpdater.Result duplicate = updater.process(event);

        assertThat(first).isEqualTo(TrackingProjectionUpdater.Result.PROCESSED);
        assertThat(duplicate).isEqualTo(TrackingProjectionUpdater.Result.DUPLICATE);
        assertThat(trackingRecords.existsById(parcelId)).isTrue();
        assertThat(trackingHistory.countByParcelId(parcelId)).isEqualTo(1);
    }

    @Test
    void updatesTheCurrentProjectionAndAppendsHistoryForANewerEvent() {
        UUID parcelId = UUID.randomUUID();
        EventEnvelope booked = parcelBookedEvent(parcelId);
        EventEnvelope inTransit = trackingEvent(
                parcelId,
                booked.data().trackingNumber(),
                "delivery.in-transit",
                "IN_TRANSIT",
                "Parcel is moving to the destination area",
                "Western Route",
                Instant.parse("2026-11-14T11:00:00Z"));

        updater.process(booked);
        updater.process(inTransit);

        TrackingRecord current = trackingRecords.findById(parcelId).orElseThrow();
        assertThat(current.currentStatus()).isEqualTo("IN_TRANSIT");
        assertThat(current.latestSafeLocationLabel()).isEqualTo("Western Route");
        assertThat(current.lastUpdatedAt()).isEqualTo(Instant.parse("2026-11-14T11:00:00Z"));
        assertThat(trackingHistory.countByParcelId(parcelId)).isEqualTo(2);
    }

    private EventEnvelope parcelBookedEvent(UUID parcelId) {
        String trackingNumber = "SWD-2026-" + String.format(
                "%06d", Math.floorMod(parcelId.hashCode(), 1_000_000));
        return trackingEvent(
                parcelId,
                trackingNumber,
                "parcel.booked",
                "BOOKED",
                "Parcel booking confirmed",
                "Colombo Hub",
                Instant.parse("2026-11-14T10:00:00Z"));
    }

    private EventEnvelope trackingEvent(
            UUID parcelId,
            String trackingNumber,
            String eventType,
            String status,
            String description,
            String location,
            Instant occurredAt) {
        TrackingEventData data = new TrackingEventData(
                parcelId,
                trackingNumber,
                status,
                description,
                location,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        return new EventEnvelope(
                UUID.randomUUID(),
                eventType,
                1,
                occurredAt,
                eventType.startsWith("parcel.")
                        ? "parcel-pricing-service"
                        : "delivery-operations-service",
                data);
    }
}
