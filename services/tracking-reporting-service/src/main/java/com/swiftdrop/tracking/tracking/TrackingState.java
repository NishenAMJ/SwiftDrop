package com.swiftdrop.tracking.tracking;

import java.time.Instant;

public record TrackingState(
        String trackingNumber,
        String status,
        String safeLocationLabel,
        Instant updatedAt) {
}
