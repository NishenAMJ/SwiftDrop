package com.swiftdrop.payment.ledger.controller;

import com.swiftdrop.payment.ledger.dto.MerchantLedgerDtos;
import com.swiftdrop.payment.ledger.service.MerchantLedgerService;
import com.swiftdrop.payment.onlinepayment.service.Actor;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/merchant-ledger")
public class MerchantLedgerController {
    private final MerchantLedgerService service;

    public MerchantLedgerController(MerchantLedgerService service) { this.service = service; }

    @GetMapping("/entries")
    public MerchantLedgerDtos.Page list(@RequestParam(required = false) UUID merchantId,
            @RequestParam(defaultValue = "LKR") String currency,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal Jwt jwt) {
        return service.list(merchantId, currency, page, size, Actor.from(jwt));
    }

    @PostMapping("/adjustments")
    public ResponseEntity<MerchantLedgerDtos.Entry> adjust(
            @Valid @RequestBody MerchantLedgerDtos.Adjustment request,
            @RequestHeader("Idempotency-Key") String key,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.adjust(request, key, Actor.from(jwt)));
    }
}
