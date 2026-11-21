package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.dto.MerchantOrderRequest;
import com.swiftdrop.parcel_pricing_service.dto.MerchantOrderResponse;
import com.swiftdrop.parcel_pricing_service.entity.OrderSource;
import com.swiftdrop.parcel_pricing_service.security.MerchantPrincipal;
import com.swiftdrop.parcel_pricing_service.service.MerchantApiAuthService;
import com.swiftdrop.parcel_pricing_service.service.MerchantOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * External merchant API (e-commerce platforms). Two ways to authenticate:
 * <ul>
 *   <li>{@code Authorization: Bearer <token>}: a MERCHANT access token, for example one from
 *       Identity's {@code POST /api/v1/auth/token} API-key exchange;</li>
 *   <li>{@code X-API-Key: <key>}: the key is exchanged with Identity on the caller's behalf.</li>
 * </ul>
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
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MerchantOrderRequest request) {
        MerchantPrincipal merchant = jwt != null ? MerchantPrincipal.fromJwt(jwt) : apiAuthService.authenticate(apiKey);
        MerchantOrderResponse created = orderService.createOrder(merchant, request, OrderSource.EXTERNAL_API);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
