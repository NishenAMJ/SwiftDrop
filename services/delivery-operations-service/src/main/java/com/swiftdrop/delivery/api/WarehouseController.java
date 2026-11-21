package com.swiftdrop.delivery.api;

import com.swiftdrop.delivery.service.WarehouseOperationsService;
import com.swiftdrop.delivery.service.WarehouseService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final WarehouseOperationsService operationsService;

    public WarehouseController(WarehouseService warehouseService, WarehouseOperationsService operationsService) {
        this.warehouseService = warehouseService;
        this.operationsService = operationsService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseResponse create(@Valid @RequestBody CreateWarehouseRequest request) {
        return warehouseService.create(request);
    }

    @GetMapping
    public Page<WarehouseResponse> list(@PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return warehouseService.list(pageable);
    }

    @GetMapping("/{warehouseId}")
    public WarehouseResponse get(@PathVariable UUID warehouseId) {
        return warehouseService.get(warehouseId);
    }

    @PutMapping("/{warehouseId}")
    public WarehouseResponse update(@PathVariable UUID warehouseId,
                                    @Valid @RequestBody UpdateWarehouseRequest request) {
        return warehouseService.update(warehouseId, request);
    }

    @PatchMapping("/{warehouseId}/status")
    public WarehouseResponse changeStatus(@PathVariable UUID warehouseId,
                                          @Valid @RequestBody WarehouseStatusRequest request) {
        return warehouseService.changeStatus(warehouseId, request.status());
    }

    @GetMapping("/{warehouseId}/dashboard")
    public WarehouseDashboardResponse dashboard(@PathVariable UUID warehouseId) {
        return operationsService.dashboard(warehouseId);
    }
}
