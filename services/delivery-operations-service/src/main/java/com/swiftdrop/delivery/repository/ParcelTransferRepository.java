package com.swiftdrop.delivery.repository;

import com.swiftdrop.delivery.domain.ParcelTransfer;
import com.swiftdrop.delivery.domain.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ParcelTransferRepository extends JpaRepository<ParcelTransfer, UUID> {
    Page<ParcelTransfer> findAllByParcelReferenceIgnoreCase(String parcelReference, Pageable pageable);
    Page<ParcelTransfer> findAllByStatus(TransferStatus status, Pageable pageable);
    List<ParcelTransfer> findTop20BySourceWarehouse_IdOrderByCreatedAtDesc(UUID warehouseId);
    List<ParcelTransfer> findTop20ByDestinationWarehouse_IdOrderByCreatedAtDesc(UUID warehouseId);
    long countBySourceWarehouse_IdAndStatus(UUID warehouseId, TransferStatus status);
    long countByDestinationWarehouse_IdAndStatus(UUID warehouseId, TransferStatus status);
    boolean existsByParcelReferenceIgnoreCaseAndStatusIn(String parcelReference, List<TransferStatus> statuses);
}
