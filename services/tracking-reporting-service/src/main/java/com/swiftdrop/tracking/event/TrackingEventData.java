package com.swiftdrop.tracking.event;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TrackingEventData(
        @NotNull UUID parcelId,
        @NotBlank
        @Size(max = 32)
        @Pattern(regexp = "^SWD-[0-9]{4}-[0-9]{6,}$") String trackingNumber,
        @NotBlank @Size(max = 30) String deliveryStatus,
        @NotBlank @Size(max = 200) String publicDescription,
        @Size(min = 1, max = 120) String publicLocation,
        UUID warehouseId,
        UUID transferId,
        UUID sourceWarehouseId,
        UUID destinationWarehouseId,
        UUID assignmentId,
        UUID courierId,
        UUID attemptId,
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$") @Size(max = 50) String failureCode) {
}
