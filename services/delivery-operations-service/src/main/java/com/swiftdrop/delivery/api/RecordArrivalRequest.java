package com.swiftdrop.delivery.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record RecordArrivalRequest(
        @NotBlank @Size(max = 50) String parcelReference,
        @NotNull UUID warehouseId,
        Instant arrivedAt,
        @Size(max = 500) String notes
) {
}
