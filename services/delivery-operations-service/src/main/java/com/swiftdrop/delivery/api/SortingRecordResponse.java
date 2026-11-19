package com.swiftdrop.delivery.api;

import com.swiftdrop.delivery.domain.SortingRecord;

import java.time.Instant;
import java.util.UUID;

public record SortingRecordResponse(
        UUID id,
        String parcelReference,
        UUID warehouseId,
        UUID nextWarehouseId,
        String destination,
        String notes,
        Instant sortedAt
) {
    public static SortingRecordResponse from(SortingRecord record) {
        return new SortingRecordResponse(record.getId(), record.getParcelReference(), record.getWarehouse().getId(),
                record.getNextWarehouse() == null ? null : record.getNextWarehouse().getId(), record.getDestination(),
                record.getNotes(), record.getSortedAt());
    }
}
