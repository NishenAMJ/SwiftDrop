package com.swiftdrop.delivery.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateTransferRequest(
        @NotBlank @Size(max = 50) String parcelReference,
        @NotNull UUID sourceWarehouseId,
        @NotNull UUID destinationWarehouseId
) {
}
