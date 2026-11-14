package com.swiftdrop.tracking.tracking;

import java.util.List;

public record TrackingHistoryResponse(
        String trackingNumber,
        List<TrackingHistoryItemResponse> events) {

    public TrackingHistoryResponse {
        events = List.copyOf(events);
    }
}
