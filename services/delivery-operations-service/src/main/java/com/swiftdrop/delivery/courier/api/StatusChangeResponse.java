package com.swiftdrop.delivery.courier.api;

import com.swiftdrop.delivery.courier.domain.AssignmentStatus;
import com.swiftdrop.delivery.courier.domain.AssignmentStatusChange;

import java.time.Instant;
import java.util.UUID;

/** One line of an assignment's audit trail. Dispatchers only. */
public record StatusChangeResponse(
        UUID id,
        AssignmentStatus fromStatus,
        AssignmentStatus toStatus,
        UUID changedBy,
        String changedByRole,
        Instant changedAt
) {
    public static StatusChangeResponse from(AssignmentStatusChange change) {
        return new StatusChangeResponse(change.getId(), change.getFromStatus(), change.getToStatus(),
                change.getChangedBy(), change.getChangedByRole(), change.getChangedAt());
    }
}
