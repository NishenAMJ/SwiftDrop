package com.swiftdrop.identity.courier;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CourierApplicationRequest(
        @NotBlank @Size(min = 5, max = 20) String nationalIdNumber,
        @NotNull VehicleType vehicleType,
        @Size(max = 20) String drivingLicenceNumber,
        @Size(max = 20) String vehicleRegistrationNumber) {

    @AssertTrue(message = "driving licence and vehicle registration numbers are required for this vehicle type")
    public boolean isVehicleDocuments() {
        return vehicleType == null || !vehicleType.requiresDocuments()
                || (hasText(drivingLicenceNumber) && hasText(vehicleRegistrationNumber));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
