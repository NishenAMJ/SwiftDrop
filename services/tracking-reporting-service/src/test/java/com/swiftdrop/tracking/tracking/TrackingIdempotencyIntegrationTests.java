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

    private EventEnvelope parcelBookedEvent(UUID parcelId) {
        TrackingEventData data = new TrackingEventData(
                parcelId,
                "SWD-2026-" + String.format("%06d", Math.floorMod(parcelId.hashCode(), 1_000_000)),
                "BOOKED",
                "Parcel booking confirmed",
                "Colombo Hub",
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
                "parcel.booked",
                1,
                Instant.parse("2026-11-14T10:00:00Z"),
                "parcel-pricing-service",
                data);
    }
}
