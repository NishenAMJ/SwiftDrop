package com.swiftdrop.delivery.courier.api;

import com.swiftdrop.delivery.courier.domain.AssignmentStatus;
import com.swiftdrop.delivery.courier.domain.DeliveryAssignment;
import com.swiftdrop.delivery.courier.parcel.ParcelContact;

import java.math.BigDecimal;
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
        BigDecimal codAmount,
        String currency,
        ParcelContact pickup,
        ParcelContact delivery,
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
                assignment.getStatus(), assignment.getNotes(), assignment.getCodAmount(),
                assignment.getCurrency(), pickupFor(assignment), deliveryFor(assignment), assignment.getAssignedAt(),
                assignment.getPickedUpAt(), assignment.getOutForDeliveryAt(), assignment.getCompletedAt(),
                assignment.getCreatedAt(), assignment.getUpdatedAt());
    }

    // Names, phone numbers and addresses are shown only while the job is open, then dropped from the API.
    private static boolean open(DeliveryAssignment assignment) {
        return assignment.getStatus() != AssignmentStatus.DELIVERED
                && assignment.getStatus() != AssignmentStatus.DELIVERY_FAILED;
    }

    private static ParcelContact pickupFor(DeliveryAssignment assignment) {
        return open(assignment) ? assignment.pickupContact() : null;
    }

    private static ParcelContact deliveryFor(DeliveryAssignment assignment) {
        return open(assignment) ? assignment.deliveryContact() : null;
    }
}
