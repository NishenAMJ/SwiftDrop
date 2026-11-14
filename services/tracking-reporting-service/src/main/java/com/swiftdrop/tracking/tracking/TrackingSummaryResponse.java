package com.swiftdrop.tracking.tracking;

import java.time.Instant;

public record TrackingSummaryResponse(
        String trackingNumber,
        String currentStatus,
        String publicDescription,
        String safeLocation,
        Instant latestUpdateTime) {
}
