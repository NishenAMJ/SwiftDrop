package com.swiftdrop.delivery.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateWarehouseRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 160) String addressLine1,
        @Size(max = 160) String addressLine2,
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Size(max = 80) String district,
        @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 120) String contactName,
        @NotBlank @Size(max = 30) String contactPhone,
        @Email @Size(max = 160) String contactEmail
) {
}
