package com.swiftdrop.delivery.courier.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCourierRequest(
        @NotNull UUID identityUserId,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Size(max = 30) String phone,
        @Size(max = 30) String vehicleNumber,
        @NotNull UUID categoryId
) {
}
