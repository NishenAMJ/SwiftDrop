package com.swiftdrop.identity.courier;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.swiftdrop.identity.approval.ApplicationStatus;
import com.swiftdrop.identity.approval.LastDecision;

/** The {@code Courier} schema from the contract. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CourierResponse(
        UUID userId,
        String fullName,
        String nationalIdNumber,
        VehicleType vehicleType,
        String drivingLicenceNumber,
        String vehicleRegistrationNumber,
        ApplicationStatus status,
        LastDecision lastDecision,
        Instant submittedAt,
        Instant createdAt) {

    /** Full view, for dispatchers. Must be called inside a transaction (reads the user's name). */
    static CourierResponse from(Courier c) {
        return new CourierResponse(c.getUserId(), c.getFullName(), c.getNationalIdNumber(), c.getVehicleType(),
                c.getDrivingLicenceNumber(), c.getVehicleRegistrationNumber(), c.getStatus(), c.lastDecision(),
                c.getSubmittedAt(), c.getCreatedAt());
    }

    /** The courier's own view: hides which dispatcher decided. */
    public CourierResponse forApplicant() {
        return new CourierResponse(userId, fullName, nationalIdNumber, vehicleType, drivingLicenceNumber,
                vehicleRegistrationNumber, status, lastDecision == null ? null : lastDecision.withoutDecider(),
                submittedAt, createdAt);
    }
}
