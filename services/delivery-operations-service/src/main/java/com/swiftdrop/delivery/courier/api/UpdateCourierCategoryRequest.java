package com.swiftdrop.delivery.courier.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateCourierCategoryRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 300) String description,
        @DecimalMin("0.01") BigDecimal maxWeightKg
) {
}
