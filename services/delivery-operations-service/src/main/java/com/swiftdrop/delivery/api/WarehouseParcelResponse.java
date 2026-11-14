package com.swiftdrop.delivery.api;

import com.swiftdrop.delivery.domain.WarehouseParcel;
import com.swiftdrop.delivery.domain.WarehouseParcelState;

import java.time.Instant;
import java.util.UUID;

public record WarehouseParcelResponse(
        UUID id,
        String parcelReference,
        UUID warehouseId,
        String warehouseCode,
        WarehouseParcelState state,
        Instant arrivedAt,
        String notes,
        Instant updatedAt
) {
    public static WarehouseParcelResponse from(WarehouseParcel parcel) {
        return new WarehouseParcelResponse(parcel.getId(), parcel.getParcelReference(), parcel.getWarehouse().getId(),
                parcel.getWarehouse().getCode(), parcel.getState(), parcel.getArrivedAt(), parcel.getArrivalNotes(),
                parcel.getUpdatedAt());
    }
}
