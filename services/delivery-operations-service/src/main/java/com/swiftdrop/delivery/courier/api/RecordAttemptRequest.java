package com.swiftdrop.delivery.courier.api;

import com.swiftdrop.delivery.courier.domain.FailureReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record RecordAttemptRequest(
        @NotNull FailureReason reason,
        @Size(max = 500) String notes,
        Instant attemptedAt
) {
}
