package com.swiftdrop.delivery.api;

import com.swiftdrop.delivery.domain.Warehouse;
import com.swiftdrop.delivery.domain.WarehouseStatus;

import java.time.Instant;
import java.util.UUID;

public record WarehouseResponse(
        UUID id,
        String code,
        String name,
        String addressLine1,
        String addressLine2,
        String city,
        String district,
        String postalCode,
        String contactName,
        String contactPhone,
        String contactEmail,
        WarehouseStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static WarehouseResponse from(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.getId(), warehouse.getCode(), warehouse.getName(), warehouse.getAddressLine1(),
                warehouse.getAddressLine2(), warehouse.getCity(), warehouse.getDistrict(), warehouse.getPostalCode(),
                warehouse.getContactName(), warehouse.getContactPhone(), warehouse.getContactEmail(),
                warehouse.getStatus(), warehouse.getCreatedAt(), warehouse.getUpdatedAt());
    }
}
