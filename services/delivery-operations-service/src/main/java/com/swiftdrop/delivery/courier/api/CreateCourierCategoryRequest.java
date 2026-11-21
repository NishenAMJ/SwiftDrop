package com.swiftdrop.delivery.courier.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateCourierCategoryRequest(
        @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
        @NotBlank @Size(max = 80) String name,
        @Size(max = 300) String description,
        @DecimalMin("0.01") BigDecimal maxWeightKg
) {
}
