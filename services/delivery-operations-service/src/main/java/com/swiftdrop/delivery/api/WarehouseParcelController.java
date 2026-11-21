package com.swiftdrop.delivery.api;

import com.swiftdrop.delivery.service.WarehouseOperationsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/warehouse-parcels")
public class WarehouseParcelController {

    private final WarehouseOperationsService operationsService;

    public WarehouseParcelController(WarehouseOperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @PostMapping("/arrivals")
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseParcelResponse recordArrival(@Valid @RequestBody RecordArrivalRequest request) {
        return operationsService.recordArrival(request);
    }

    @GetMapping("/{parcelReference}")
    public WarehouseParcelResponse getParcel(@PathVariable String parcelReference) {
        return operationsService.getParcel(parcelReference);
    }
}
