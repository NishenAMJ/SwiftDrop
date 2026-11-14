package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.dto.MerchantOrderRequest;
import com.swiftdrop.parcel_pricing_service.dto.MerchantOrderResponse;
import com.swiftdrop.parcel_pricing_service.entity.OrderSource;
import com.swiftdrop.parcel_pricing_service.service.MerchantApiAuthService;
import com.swiftdrop.parcel_pricing_service.service.MerchantOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * External merchant API (e-commerce platforms). Authenticated with the merchant's API key in
 * the {@code X-API-Key} header; key verification is delegated to Member 2's verifier.
 * Uses the same {@link MerchantOrderService#createOrder} as the dashboard.
 */
@RestController
@RequestMapping("/api/integrations/merchant/orders")
public class MerchantIntegrationController {

    private final MerchantOrderService orderService;
    private final MerchantApiAuthService apiAuthService;

    public MerchantIntegrationController(MerchantOrderService orderService, MerchantApiAuthService apiAuthService) {
        this.orderService = orderService;
        this.apiAuthService = apiAuthService;
    }

    @PostMapping
    public ResponseEntity<MerchantOrderResponse> createOrder(
            @RequestHeader(name = "X-API-Key", required = false) String apiKey,
            @Valid @RequestBody MerchantOrderRequest request) {
        Long merchantId = apiAuthService.authenticate(apiKey);
        MerchantOrderResponse created = orderService.createOrder(merchantId, request, OrderSource.EXTERNAL_API);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
