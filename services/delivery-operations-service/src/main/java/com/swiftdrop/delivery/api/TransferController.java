package com.swiftdrop.delivery.api;

import com.swiftdrop.delivery.domain.TransferStatus;
import com.swiftdrop.delivery.service.WarehouseOperationsService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final WarehouseOperationsService operationsService;

    public TransferController(WarehouseOperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse create(@Valid @RequestBody CreateTransferRequest request) {
        return operationsService.createTransfer(request);
    }

    @GetMapping
    public Page<TransferResponse> list(@RequestParam(required = false) String parcelReference,
                                       @RequestParam(required = false) TransferStatus status,
                                       @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return operationsService.listTransfers(parcelReference, status, pageable);
    }

    @GetMapping("/{transferId}")
    public TransferResponse get(@PathVariable UUID transferId) {
        return operationsService.getTransfer(transferId);
    }

    @PostMapping("/{transferId}/dispatch")
    public TransferResponse dispatch(@PathVariable UUID transferId) {
        return operationsService.dispatch(transferId);
    }

    @PostMapping("/{transferId}/receive")
    public TransferResponse receive(@PathVariable UUID transferId,
                                    @Valid @RequestBody ReceiveTransferRequest request) {
        return operationsService.receive(transferId, request);
    }

    @PostMapping("/{transferId}/cancel")
    public TransferResponse cancel(@PathVariable UUID transferId) {
        return operationsService.cancel(transferId);
    }
}
