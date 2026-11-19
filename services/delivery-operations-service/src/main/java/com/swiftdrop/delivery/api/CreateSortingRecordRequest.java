package com.swiftdrop.delivery.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateSortingRecordRequest(
        @NotBlank @Size(max = 50) String parcelReference,
        @NotNull UUID warehouseId,
        UUID nextWarehouseId,
        @NotBlank @Size(max = 160) String destination,
        @Size(max = 500) String notes,
        Instant sortedAt
) {
}
