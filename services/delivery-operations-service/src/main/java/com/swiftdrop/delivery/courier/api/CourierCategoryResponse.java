package com.swiftdrop.delivery.courier.api;

import com.swiftdrop.delivery.courier.domain.CategoryStatus;
import com.swiftdrop.delivery.courier.domain.CourierCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CourierCategoryResponse(
        UUID id,
        String code,
        String name,
        String description,
        BigDecimal maxWeightKg,
        CategoryStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static CourierCategoryResponse from(CourierCategory category) {
        return new CourierCategoryResponse(category.getId(), category.getCode(), category.getName(),
                category.getDescription(), category.getMaxWeightKg(), category.getStatus(),
                category.getCreatedAt(), category.getUpdatedAt());
    }
}
