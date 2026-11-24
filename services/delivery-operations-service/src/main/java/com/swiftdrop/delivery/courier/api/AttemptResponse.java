package com.swiftdrop.delivery.courier.api;

import com.swiftdrop.delivery.courier.domain.DeliveryAttempt;
import com.swiftdrop.delivery.courier.domain.FailureReason;

import java.time.Instant;
import java.util.UUID;

public record AttemptResponse(
        UUID id,
        UUID assignmentId,
        String parcelReference,
        int attemptNumber,
        FailureReason reason,
        String notes,
        Instant attemptedAt
) {
    public static AttemptResponse from(DeliveryAttempt attempt) {
        return new AttemptResponse(attempt.getId(), attempt.getAssignment().getId(), attempt.getParcelReference(),
                attempt.getAttemptNumber(), attempt.getReason(), attempt.getNotes(), attempt.getAttemptedAt());
    }
}
