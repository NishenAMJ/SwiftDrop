package com.swiftdrop.tracking.tracking;

import java.time.Instant;

public record TrackingHistoryItemResponse(
        String status,
        String publicDescription,
        String safeLocation,
        Instant eventTime) {
}
