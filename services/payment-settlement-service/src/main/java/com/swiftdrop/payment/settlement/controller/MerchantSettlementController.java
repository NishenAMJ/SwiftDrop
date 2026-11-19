package com.swiftdrop.payment.settlement.controller;

import com.swiftdrop.payment.onlinepayment.service.Actor;
import com.swiftdrop.payment.settlement.dto.MerchantSettlementDtos;
import com.swiftdrop.payment.settlement.service.MerchantSettlementService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/merchant-settlements")
public class MerchantSettlementController {
    private final MerchantSettlementService service;

    public MerchantSettlementController(MerchantSettlementService service) { this.service = service; }

    @PostMapping("/calculate")
    public ResponseEntity<MerchantSettlementDtos.View> calculate(
            @Valid @RequestBody MerchantSettlementDtos.Calculate request,
            @RequestHeader("Idempotency-Key") String key,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.calculate(request, key, Actor.from(jwt)));
    }

    @GetMapping("/{settlementId}")
    public MerchantSettlementDtos.View get(@PathVariable UUID settlementId,
            @AuthenticationPrincipal Jwt jwt) {
        return service.get(settlementId, Actor.from(jwt));
    }

    @PostMapping("/{settlementId}/approve")
    public MerchantSettlementDtos.View approve(@PathVariable UUID settlementId,
            @Valid @RequestBody MerchantSettlementDtos.Approve request,
            @RequestHeader("Idempotency-Key") String key,
            @AuthenticationPrincipal Jwt jwt) {
        return service.approve(settlementId, request, key, Actor.from(jwt));
    }

    @PostMapping("/{settlementId}/record-payment")
    public MerchantSettlementDtos.View recordPayment(@PathVariable UUID settlementId,
            @Valid @RequestBody MerchantSettlementDtos.RecordPayment request,
            @RequestHeader("Idempotency-Key") String key,
            @AuthenticationPrincipal Jwt jwt) {
        return service.recordPayment(settlementId, request, key, Actor.from(jwt));
    }
}
