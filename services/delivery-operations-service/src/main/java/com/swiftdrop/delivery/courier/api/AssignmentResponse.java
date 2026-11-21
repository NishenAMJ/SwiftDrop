package com.swiftdrop.delivery.courier.api;

import com.swiftdrop.delivery.courier.domain.AssignmentStatus;
import com.swiftdrop.delivery.courier.domain.DeliveryAssignment;

import java.time.Instant;
import java.util.UUID;

public record AssignmentResponse(
        UUID id,
        String parcelReference,
        UUID courierId,
        String courierName,
        String categoryCode,
        String assignedBy,
        AssignmentStatus status,
        String notes,
        Instant assignedAt,
        Instant pickedUpAt,
        Instant outForDeliveryAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static AssignmentResponse from(DeliveryAssignment assignment) {
        var courier = assignment.getCourier();
        return new AssignmentResponse(assignment.getId(), assignment.getParcelReference(), courier.getId(),
                courier.getFullName(), courier.getCategory().getCode(), assignment.getAssignedBy(),
                assignment.getStatus(), assignment.getNotes(), assignment.getAssignedAt(),
                assignment.getPickedUpAt(), assignment.getOutForDeliveryAt(), assignment.getCompletedAt(),
                assignment.getCreatedAt(), assignment.getUpdatedAt());
    }
}
