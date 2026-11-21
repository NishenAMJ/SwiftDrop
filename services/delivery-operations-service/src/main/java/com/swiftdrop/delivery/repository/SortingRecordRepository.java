package com.swiftdrop.delivery.repository;

import com.swiftdrop.delivery.domain.SortingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SortingRecordRepository extends JpaRepository<SortingRecord, UUID> {
    long countByWarehouse_Id(UUID warehouseId);
}
