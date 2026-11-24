package com.swiftdrop.payment.reconciliation.controller;

import com.swiftdrop.payment.onlinepayment.service.Actor;
import com.swiftdrop.payment.reconciliation.dto.CashHandoverDtos;
import com.swiftdrop.payment.reconciliation.service.CashHandoverService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cod-handovers")
public class CashHandoverController {
    private final CashHandoverService service;

    public CashHandoverController(CashHandoverService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<CashHandoverDtos.Handover> create(
            @Valid @RequestBody CashHandoverDtos.Create request,
            @RequestHeader("Idempotency-Key") String key,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, key, Actor.from(jwt)));
    }

    @GetMapping
    public CashHandoverDtos.Page list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID courierId,
            @AuthenticationPrincipal Jwt jwt) {
        return service.list(page, size, status, courierId, Actor.from(jwt));
    }

    @GetMapping("/{handoverId}")
    public CashHandoverDtos.Handover get(@PathVariable UUID handoverId,
            @AuthenticationPrincipal Jwt jwt) {
        return service.get(handoverId, Actor.from(jwt));
    }

    @PostMapping("/{handoverId}/confirm")
    public CashHandoverDtos.Handover confirm(@PathVariable UUID handoverId,
            @Valid @RequestBody CashHandoverDtos.Confirm request,
            @RequestHeader("Idempotency-Key") String key,
            @AuthenticationPrincipal Jwt jwt) {
        return service.confirm(handoverId, request, key, Actor.from(jwt));
    }
}
