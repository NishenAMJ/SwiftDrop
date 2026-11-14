package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.dto.MerchantOrderRequest;
import com.swiftdrop.parcel_pricing_service.dto.MerchantOrderResponse;
import com.swiftdrop.parcel_pricing_service.entity.OrderSource;
import com.swiftdrop.parcel_pricing_service.security.MerchantIdentityResolver;
import com.swiftdrop.parcel_pricing_service.service.MerchantOrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/** Merchant Dashboard endpoints. The merchant is identified from authentication, never from the body. */
@RestController
@RequestMapping("/api/merchant/orders")
public class MerchantOrderController {

    private final MerchantOrderService orderService;
    private final MerchantIdentityResolver identityResolver;

    public MerchantOrderController(MerchantOrderService orderService, MerchantIdentityResolver identityResolver) {
        this.orderService = orderService;
        this.identityResolver = identityResolver;
    }

    @PostMapping
    public ResponseEntity<MerchantOrderResponse> createOrder(@Valid @RequestBody MerchantOrderRequest request,
                                                             HttpServletRequest httpRequest) {
        Long merchantId = identityResolver.resolveMerchantId(httpRequest);
        MerchantOrderResponse created = orderService.createOrder(merchantId, request, OrderSource.DASHBOARD);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Orders of the authenticated merchant, newest first. Optional {@code page} (0-based) and
     * {@code size} (default 20, max 100); the total is returned in the {@code X-Total-Count} header.
     */
    @GetMapping
    public ResponseEntity<List<MerchantOrderResponse>> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Long merchantId = identityResolver.resolveMerchantId(httpRequest);
        Page<MerchantOrderResponse> result = orderService.getOrders(merchantId, page, size);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(result.getTotalElements()))
                .body(result.getContent());
    }

    @GetMapping("/{id}")
    public MerchantOrderResponse getOrder(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long merchantId = identityResolver.resolveMerchantId(httpRequest);
        return orderService.getOrder(merchantId, id);
    }
}
