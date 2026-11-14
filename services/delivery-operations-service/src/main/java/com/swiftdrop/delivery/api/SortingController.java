package com.swiftdrop.delivery.api;

import com.swiftdrop.delivery.service.WarehouseOperationsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sorting-records")
public class SortingController {

    private final WarehouseOperationsService operationsService;

    public SortingController(WarehouseOperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SortingRecordResponse sort(@Valid @RequestBody CreateSortingRecordRequest request) {
        return operationsService.sort(request);
    }
}
