package com.swiftdrop.delivery.courier.api;

import com.swiftdrop.delivery.courier.domain.AssignmentStatus;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record UpdateAssignmentStatusRequest(@NotNull AssignmentStatus status, Instant occurredAt) {
}
