package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.dto.MerchantOrderRequest;
import com.swiftdrop.parcel_pricing_service.dto.MerchantOrderResponse;
import com.swiftdrop.parcel_pricing_service.entity.OrderSource;
import com.swiftdrop.parcel_pricing_service.security.MerchantPrincipal;
import com.swiftdrop.parcel_pricing_service.service.MerchantOrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
import java.util.UUID;

/**
 * Merchant Dashboard endpoints (role MERCHANT, see SecurityConfig). The merchant is taken from the
 * verified access token (subject and {@code merchant_id} claim), never from the body or a header.
 */
@RestController
@RequestMapping("/api/merchant/orders")
public class MerchantOrderController {

    private final MerchantOrderService orderService;

    public MerchantOrderController(MerchantOrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<MerchantOrderResponse> createOrder(@Valid @RequestBody MerchantOrderRequest request,
                                                             @AuthenticationPrincipal Jwt jwt) {
        MerchantOrderResponse created = orderService.createOrder(MerchantPrincipal.fromJwt(jwt), request, OrderSource.DASHBOARD);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Orders of the authenticated merchant, newest first. Optional {@code page} (0-based) and
     * {@code size} (default 20, max 100); the total is returned in the {@code X-Total-Count} header.
     */
    @GetMapping
    public ResponseEntity<List<MerchantOrderResponse>> getOrders(@RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size,
                                                                 @AuthenticationPrincipal Jwt jwt) {
        Page<MerchantOrderResponse> result = orderService.getOrders(MerchantPrincipal.fromJwt(jwt), page, size);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(result.getTotalElements()))
                .body(result.getContent());
    }

    @GetMapping("/{id}")
    public MerchantOrderResponse getOrder(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return orderService.getOrder(MerchantPrincipal.fromJwt(jwt), id);
    }
}
