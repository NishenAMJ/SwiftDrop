package com.swiftdrop.delivery.repository;

import com.swiftdrop.delivery.domain.WarehouseParcel;
import com.swiftdrop.delivery.domain.WarehouseParcelState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseParcelRepository extends JpaRepository<WarehouseParcel, UUID> {
    Optional<WarehouseParcel> findByParcelReferenceIgnoreCase(String parcelReference);
    List<WarehouseParcel> findTop20ByWarehouse_IdOrderByUpdatedAtDesc(UUID warehouseId);
    long countByWarehouse_IdAndState(UUID warehouseId, WarehouseParcelState state);
}
