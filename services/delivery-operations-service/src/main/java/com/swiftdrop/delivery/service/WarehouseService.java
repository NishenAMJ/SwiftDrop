package com.swiftdrop.delivery.service;

import com.swiftdrop.delivery.api.CreateWarehouseRequest;
import com.swiftdrop.delivery.api.UpdateWarehouseRequest;
import com.swiftdrop.delivery.api.WarehouseResponse;
import com.swiftdrop.delivery.domain.Warehouse;
import com.swiftdrop.delivery.domain.WarehouseStatus;
import com.swiftdrop.delivery.exception.ConflictException;
import com.swiftdrop.delivery.exception.ResourceNotFoundException;
import com.swiftdrop.delivery.repository.WarehouseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    public WarehouseService(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    @Transactional
    public WarehouseResponse create(CreateWarehouseRequest request) {
        var code = request.code().trim().toUpperCase(Locale.ROOT);
        if (warehouseRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Warehouse code already exists: " + code);
        }

        var warehouse = new Warehouse(code, clean(request.name()), clean(request.addressLine1()),
                cleanNullable(request.addressLine2()), clean(request.city()), clean(request.district()),
                cleanNullable(request.postalCode()), clean(request.contactName()), clean(request.contactPhone()),
                cleanNullable(request.contactEmail()));
        return WarehouseResponse.from(warehouseRepository.save(warehouse));
    }

    public Page<WarehouseResponse> list(Pageable pageable) {
        return warehouseRepository.findAll(pageable).map(WarehouseResponse::from);
    }

    public WarehouseResponse get(UUID id) {
        return WarehouseResponse.from(requireWarehouse(id));
    }

    @Transactional
    public WarehouseResponse update(UUID id, UpdateWarehouseRequest request) {
        var warehouse = requireWarehouse(id);
        warehouse.update(clean(request.name()), clean(request.addressLine1()), cleanNullable(request.addressLine2()),
                clean(request.city()), clean(request.district()), cleanNullable(request.postalCode()),
                clean(request.contactName()), clean(request.contactPhone()), cleanNullable(request.contactEmail()));
        return WarehouseResponse.from(warehouse);
    }

    @Transactional
    public WarehouseResponse changeStatus(UUID id, WarehouseStatus status) {
        var warehouse = requireWarehouse(id);
        warehouse.changeStatus(status);
        return WarehouseResponse.from(warehouse);
    }

    public Warehouse requireWarehouse(UUID id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found: " + id));
    }

    private String clean(String value) {
        return value.trim();
    }

    private String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
