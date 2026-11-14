package com.swiftdrop.delivery.repository;

import com.swiftdrop.delivery.domain.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
    boolean existsByCodeIgnoreCase(String code);
}
