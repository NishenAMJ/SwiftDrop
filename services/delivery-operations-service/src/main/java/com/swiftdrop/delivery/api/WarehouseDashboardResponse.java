package com.swiftdrop.delivery.api;

import java.util.List;

public record WarehouseDashboardResponse(
        WarehouseResponse warehouse,
        long arrivedParcels,
        long sortedParcels,
        long parcelsInTransfer,
        long incomingTransfers,
        long outgoingTransfers,
        long totalSortingRecords,
        List<WarehouseParcelResponse> recentParcels,
        List<TransferResponse> recentIncomingTransfers,
        List<TransferResponse> recentOutgoingTransfers
) {
}
