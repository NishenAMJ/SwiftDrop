package com.swiftdrop.tracking.tracking;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.tracking.event.EventEnvelope;
import com.swiftdrop.tracking.live.TrackingStateChangedEvent;

@Service
public class TrackingProjectionUpdater {

    public enum Result {
        PROCESSED,
        DUPLICATE
    }

    private final ProcessedEventClaimRepository processedEvents;
    private final TrackingRecordRepository trackingRecords;
    private final TrackingHistoryRepository trackingHistory;
    private final ApplicationEventPublisher eventPublisher;

    public TrackingProjectionUpdater(
            ProcessedEventClaimRepository processedEvents,
            TrackingRecordRepository trackingRecords,
            TrackingHistoryRepository trackingHistory,
            ApplicationEventPublisher eventPublisher) {
        this.processedEvents = processedEvents;
        this.trackingRecords = trackingRecords;
        this.trackingHistory = trackingHistory;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Result process(EventEnvelope event) {
        if (!processedEvents.tryClaim(event.eventId(), event.eventType())) {
            return Result.DUPLICATE;
        }

        TrackingRecord record = trackingRecords.findById(event.data().parcelId())
                .orElseGet(() -> TrackingRecord.fromFirstEvent(event.data(), event.occurredAt()));
        boolean projectionChanged = record.apply(event.data(), event.occurredAt());

        trackingRecords.save(record);
        trackingHistory.save(TrackingHistory.from(event));

        if (projectionChanged) {
            eventPublisher.publishEvent(new TrackingStateChangedEvent(record.toState()));
        }
        return Result.PROCESSED;
    }
}
