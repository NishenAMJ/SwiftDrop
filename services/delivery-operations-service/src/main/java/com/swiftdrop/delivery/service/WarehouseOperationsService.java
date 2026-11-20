package com.swiftdrop.delivery.service;

import com.swiftdrop.delivery.api.CreateSortingRecordRequest;
import com.swiftdrop.delivery.api.CreateTransferRequest;
import com.swiftdrop.delivery.api.ReceiveTransferRequest;
import com.swiftdrop.delivery.api.RecordArrivalRequest;
import com.swiftdrop.delivery.api.SortingRecordResponse;
import com.swiftdrop.delivery.api.TransferResponse;
import com.swiftdrop.delivery.api.WarehouseDashboardResponse;
import com.swiftdrop.delivery.api.WarehouseParcelResponse;
import com.swiftdrop.delivery.api.WarehouseResponse;
import com.swiftdrop.delivery.domain.ParcelTransfer;
import com.swiftdrop.delivery.domain.SortingRecord;
import com.swiftdrop.delivery.domain.TransferStatus;
import com.swiftdrop.delivery.domain.Warehouse;
import com.swiftdrop.delivery.domain.WarehouseParcel;
import com.swiftdrop.delivery.domain.WarehouseParcelState;
import com.swiftdrop.delivery.domain.WarehouseStatus;
import com.swiftdrop.delivery.exception.ConflictException;
import com.swiftdrop.delivery.exception.InvalidOperationException;
import com.swiftdrop.delivery.exception.ResourceNotFoundException;
import com.swiftdrop.delivery.repository.ParcelTransferRepository;
import com.swiftdrop.delivery.repository.SortingRecordRepository;
import com.swiftdrop.delivery.repository.WarehouseParcelRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class WarehouseOperationsService {

    private final WarehouseService warehouseService;
    private final WarehouseParcelRepository warehouseParcelRepository;
    private final SortingRecordRepository sortingRecordRepository;
    private final ParcelTransferRepository parcelTransferRepository;

    public WarehouseOperationsService(WarehouseService warehouseService,
                                      WarehouseParcelRepository warehouseParcelRepository,
                                      SortingRecordRepository sortingRecordRepository,
                                      ParcelTransferRepository parcelTransferRepository) {
        this.warehouseService = warehouseService;
        this.warehouseParcelRepository = warehouseParcelRepository;
        this.sortingRecordRepository = sortingRecordRepository;
        this.parcelTransferRepository = parcelTransferRepository;
    }

    @Transactional
    public WarehouseParcelResponse recordArrival(RecordArrivalRequest request) {
        var reference = normalizeReference(request.parcelReference());
        if (warehouseParcelRepository.findByParcelReferenceIgnoreCase(reference).isPresent()) {
            throw new ConflictException("Parcel is already registered in warehouse operations: " + reference);
        }
        var warehouse = requireOperationalWarehouse(request.warehouseId());
        var arrival = new WarehouseParcel(reference, warehouse,
                request.arrivedAt() == null ? Instant.now() : request.arrivedAt(), cleanNullable(request.notes()));
        return WarehouseParcelResponse.from(warehouseParcelRepository.save(arrival));
    }

    public WarehouseParcelResponse getParcel(String parcelReference) {
        return WarehouseParcelResponse.from(requireParcel(normalizeReference(parcelReference)));
    }

    @Transactional
    public SortingRecordResponse sort(CreateSortingRecordRequest request) {
        var reference = normalizeReference(request.parcelReference());
        var parcel = requireParcel(reference);
        var warehouse = requireOperationalWarehouse(request.warehouseId());
        if (!parcel.getWarehouse().getId().equals(warehouse.getId())) {
            throw new InvalidOperationException("Parcel is not currently at the selected warehouse");
        }
        if (parcel.getState() == WarehouseParcelState.IN_TRANSFER) {
            throw new InvalidOperationException("A parcel in transfer cannot be sorted");
        }

        Warehouse nextWarehouse = null;
        if (request.nextWarehouseId() != null) {
            nextWarehouse = requireOperationalWarehouse(request.nextWarehouseId());
            if (warehouse.getId().equals(nextWarehouse.getId())) {
                throw new InvalidOperationException("Next warehouse must differ from the current warehouse");
            }
        }

        var record = new SortingRecord(reference, warehouse, nextWarehouse, request.destination().trim(),
                cleanNullable(request.notes()), request.sortedAt() == null ? Instant.now() : request.sortedAt());
        parcel.markSorted();
        return SortingRecordResponse.from(sortingRecordRepository.save(record));
    }

    @Transactional
    public TransferResponse createTransfer(CreateTransferRequest request) {
        if (request.sourceWarehouseId().equals(request.destinationWarehouseId())) {
            throw new InvalidOperationException("Source and destination warehouses must differ");
        }
        var reference = normalizeReference(request.parcelReference());
        var parcel = requireParcel(reference);
        var source = requireOperationalWarehouse(request.sourceWarehouseId());
        var destination = requireOperationalWarehouse(request.destinationWarehouseId());

        if (!parcel.getWarehouse().getId().equals(source.getId())) {
            throw new InvalidOperationException("Parcel is not currently at the source warehouse");
        }
        if (parcel.getState() != WarehouseParcelState.SORTED) {
            throw new InvalidOperationException("Parcel must be sorted before creating a transfer");
        }
        if (parcelTransferRepository.existsByParcelReferenceIgnoreCaseAndStatusIn(reference,
                List.of(TransferStatus.CREATED, TransferStatus.IN_TRANSIT))) {
            throw new ConflictException("Parcel already has an active transfer: " + reference);
        }

        return TransferResponse.from(parcelTransferRepository.save(new ParcelTransfer(reference, source, destination)));
    }

    @Transactional
    public TransferResponse dispatch(UUID transferId) {
        var transfer = requireTransfer(transferId);
        var parcel = requireParcel(transfer.getParcelReference());
        transfer.dispatch(Instant.now());
        parcel.markInTransfer();
        return TransferResponse.from(transfer);
    }

    @Transactional
    public TransferResponse receive(UUID transferId, ReceiveTransferRequest request) {
        var transfer = requireTransfer(transferId);
        var receivedAt = request.receivedAt() == null ? Instant.now() : request.receivedAt();
        transfer.receive(receivedAt, request.receivedBy().trim(), cleanNullable(request.notes()));
        var parcel = requireParcel(transfer.getParcelReference());
        parcel.receiveAt(transfer.getDestinationWarehouse(), receivedAt);
        return TransferResponse.from(transfer);
    }

    @Transactional
    public TransferResponse cancel(UUID transferId) {
        var transfer = requireTransfer(transferId);
        transfer.cancel();
        return TransferResponse.from(transfer);
    }

    public TransferResponse getTransfer(UUID transferId) {
        return TransferResponse.from(requireTransfer(transferId));
    }

    public Page<TransferResponse> listTransfers(String parcelReference, TransferStatus status, Pageable pageable) {
        if (parcelReference != null && !parcelReference.isBlank()) {
            return parcelTransferRepository
                    .findAllByParcelReferenceIgnoreCase(normalizeReference(parcelReference), pageable)
                    .map(TransferResponse::from);
        }
        if (status != null) {
            return parcelTransferRepository.findAllByStatus(status, pageable).map(TransferResponse::from);
        }
        return parcelTransferRepository.findAll(pageable).map(TransferResponse::from);
    }

    public WarehouseDashboardResponse dashboard(UUID warehouseId) {
        var warehouse = warehouseService.requireWarehouse(warehouseId);
        var recentParcels = warehouseParcelRepository.findTop20ByWarehouse_IdOrderByUpdatedAtDesc(warehouseId)
                .stream().map(WarehouseParcelResponse::from).toList();
        var incoming = parcelTransferRepository.findTop20ByDestinationWarehouse_IdOrderByCreatedAtDesc(warehouseId)
                .stream().map(TransferResponse::from).toList();
        var outgoing = parcelTransferRepository.findTop20BySourceWarehouse_IdOrderByCreatedAtDesc(warehouseId)
                .stream().map(TransferResponse::from).toList();

        return new WarehouseDashboardResponse(WarehouseResponse.from(warehouse),
                warehouseParcelRepository.countByWarehouse_IdAndState(warehouseId, WarehouseParcelState.ARRIVED),
                warehouseParcelRepository.countByWarehouse_IdAndState(warehouseId, WarehouseParcelState.SORTED),
                warehouseParcelRepository.countByWarehouse_IdAndState(warehouseId, WarehouseParcelState.IN_TRANSFER),
                parcelTransferRepository.countByDestinationWarehouse_IdAndStatus(warehouseId, TransferStatus.IN_TRANSIT),
                parcelTransferRepository.countBySourceWarehouse_IdAndStatus(warehouseId, TransferStatus.IN_TRANSIT),
                sortingRecordRepository.countByWarehouse_Id(warehouseId), recentParcels, incoming, outgoing);
    }

    private Warehouse requireOperationalWarehouse(UUID id) {
        var warehouse = warehouseService.requireWarehouse(id);
        if (warehouse.getStatus() != WarehouseStatus.ACTIVE) {
            throw new InvalidOperationException("Warehouse is not active: " + warehouse.getCode());
        }
        return warehouse;
    }

    private WarehouseParcel requireParcel(String reference) {
        return warehouseParcelRepository.findByParcelReferenceIgnoreCase(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Parcel not found in warehouse operations: " + reference));
    }

    private ParcelTransfer requireTransfer(UUID id) {
        return parcelTransferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found: " + id));
    }

    private String normalizeReference(String reference) {
        return reference.trim().toUpperCase(Locale.ROOT);
    }

    private String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
