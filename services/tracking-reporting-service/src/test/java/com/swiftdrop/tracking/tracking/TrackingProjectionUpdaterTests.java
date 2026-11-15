package com.swiftdrop.tracking.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.swiftdrop.tracking.event.EventEnvelope;
import com.swiftdrop.tracking.event.TrackingEventData;
import com.swiftdrop.tracking.live.TrackingStateChangedEvent;

@ExtendWith(MockitoExtension.class)
class TrackingProjectionUpdaterTests {

    @Mock
    private ProcessedEventClaimRepository processedEvents;

    @Mock
    private TrackingRecordRepository trackingRecords;

    @Mock
    private TrackingHistoryRepository trackingHistory;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TrackingProjectionUpdater updater;

    @BeforeEach
    void setUp() {
        updater = new TrackingProjectionUpdater(
                processedEvents,
                trackingRecords,
                trackingHistory,
                eventPublisher);
    }

    @Test
    void acknowledgesADuplicateWithoutChangingTheProjection() {
        EventEnvelope event = parcelBookedEvent();
        when(processedEvents.tryClaim(event.eventId(), event.eventType())).thenReturn(false);

        TrackingProjectionUpdater.Result result = updater.process(event);

        assertThat(result).isEqualTo(TrackingProjectionUpdater.Result.DUPLICATE);
        verify(trackingRecords, never()).save(any());
        verify(trackingHistory, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void createsProjectionHistoryAndCommittedStateEvent() {
        EventEnvelope event = parcelBookedEvent();
        when(processedEvents.tryClaim(event.eventId(), event.eventType())).thenReturn(true);
        when(trackingRecords.findById(event.data().parcelId())).thenReturn(Optional.empty());

        TrackingProjectionUpdater.Result result = updater.process(event);

        assertThat(result).isEqualTo(TrackingProjectionUpdater.Result.PROCESSED);
        verify(trackingRecords).save(any(TrackingRecord.class));
        verify(trackingHistory).save(any(TrackingHistory.class));
        verify(eventPublisher).publishEvent(any(TrackingStateChangedEvent.class));
    }

    private EventEnvelope parcelBookedEvent() {
        TrackingEventData data = new TrackingEventData(
                UUID.randomUUID(),
                "SWD-2026-100002",
                "BOOKED",
                "Parcel booking confirmed",
                null,
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
