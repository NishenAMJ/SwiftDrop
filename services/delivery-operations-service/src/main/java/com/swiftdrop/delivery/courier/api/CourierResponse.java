package com.swiftdrop.delivery.courier.api;

import com.swiftdrop.delivery.courier.domain.Courier;
import com.swiftdrop.delivery.courier.domain.CourierStatus;

import java.time.Instant;
import java.util.UUID;

public record CourierResponse(
        UUID id,
        UUID identityUserId,
        String fullName,
        String phone,
        String vehicleNumber,
        UUID categoryId,
        String categoryCode,
        String categoryName,
        CourierStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static CourierResponse from(Courier courier) {
        return new CourierResponse(courier.getId(), courier.getIdentityUserId(), courier.getFullName(),
                courier.getPhone(), courier.getVehicleNumber(), courier.getCategory().getId(),
                courier.getCategory().getCode(), courier.getCategory().getName(), courier.getStatus(),
                courier.getCreatedAt(), courier.getUpdatedAt());
    }
}
