package com.swiftdrop.delivery.courier.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AssignParcelRequest(
        @NotBlank @Size(max = 50) String parcelReference,
        @NotNull UUID courierId,
        @NotBlank @Size(max = 120) String assignedBy,
        @Size(max = 500) String notes
) {
}
