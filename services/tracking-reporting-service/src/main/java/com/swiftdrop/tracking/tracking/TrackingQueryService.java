package com.swiftdrop.tracking.tracking;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.tracking.common.ApiException;

@Service
public class TrackingQueryService {

    private final TrackingRecordRepository trackingRecords;
    private final TrackingHistoryRepository trackingHistory;

    public TrackingQueryService(
            TrackingRecordRepository trackingRecords,
            TrackingHistoryRepository trackingHistory) {
        this.trackingRecords = trackingRecords;
        this.trackingHistory = trackingHistory;
    }

    @Transactional(readOnly = true)
    public TrackingSummaryResponse getSummary(String trackingNumber) {
        TrackingRecord record = requireRecord(trackingNumber);
        TrackingHistory latest = trackingHistory
                .findFirstByParcelIdOrderByEventTimeDescHistoryIdDesc(record.parcelId())
                .orElseThrow(TrackingQueryService::notFound);

        return new TrackingSummaryResponse(
                record.trackingNumber(),
                record.currentStatus(),
                latest.publicDescription(),
                record.latestSafeLocationLabel(),
                record.lastUpdatedAt());
    }

    @Transactional(readOnly = true)
    public TrackingHistoryResponse getHistory(String trackingNumber) {
        TrackingRecord record = requireRecord(trackingNumber);
        List<TrackingHistoryItemResponse> events = trackingHistory
                .findByParcelIdOrderByEventTimeAscHistoryIdAsc(record.parcelId())
                .stream()
                .map(history -> new TrackingHistoryItemResponse(
                        history.status(),
                        history.publicDescription(),
                        history.safeLocationLabel(),
                        history.eventTime()))
                .toList();
        return new TrackingHistoryResponse(record.trackingNumber(), events);
    }

    @Transactional(readOnly = true)
    public void requireTrackingNumber(String trackingNumber) {
        requireRecord(trackingNumber);
    }

    private TrackingRecord requireRecord(String trackingNumber) {
        return trackingRecords.findByTrackingNumber(trackingNumber)
                .orElseThrow(TrackingQueryService::notFound);
    }

    private static ApiException notFound() {
        return ApiException.notFound("Tracking information was not found.");
    }
}
