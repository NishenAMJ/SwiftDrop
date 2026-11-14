package com.swiftdrop.delivery.api;

import com.swiftdrop.delivery.domain.ParcelTransfer;
import com.swiftdrop.delivery.domain.TransferStatus;

import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        String parcelReference,
        UUID sourceWarehouseId,
        String sourceWarehouseCode,
        UUID destinationWarehouseId,
        String destinationWarehouseCode,
        TransferStatus status,
        Instant transferDate,
        Instant receivedAt,
        String receivedBy,
        String receivingNotes,
        Instant createdAt,
        Instant updatedAt
) {
    public static TransferResponse from(ParcelTransfer transfer) {
        return new TransferResponse(transfer.getId(), transfer.getParcelReference(),
                transfer.getSourceWarehouse().getId(), transfer.getSourceWarehouse().getCode(),
                transfer.getDestinationWarehouse().getId(), transfer.getDestinationWarehouse().getCode(),
                transfer.getStatus(), transfer.getTransferDate(), transfer.getReceivedAt(), transfer.getReceivedBy(),
                transfer.getReceivingNotes(), transfer.getCreatedAt(), transfer.getUpdatedAt());
    }
}
