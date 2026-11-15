package com.swiftdrop.payment.onlinepayment.controller;

import com.swiftdrop.payment.onlinepayment.dto.PaymentDtos.*;
import com.swiftdrop.payment.onlinepayment.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService service;
    public PaymentController(PaymentService service) { this.service=service; }
    @PostMapping
    ResponseEntity<Map<String,Object>> create(@Valid @RequestBody Create body,@RequestHeader("Idempotency-Key") String key,@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(service.create(body,key,Actor.from(jwt)));
    }
    @GetMapping("/{id}") View get(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) { return service.get(id,Actor.from(jwt)); }
    @GetMapping("/{id}/checkout") ResponseEntity<Checkout> checkout(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.checkout(id,Actor.from(jwt)));
    }
    @GetMapping("/history") Page<View> history(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam(defaultValue="createdAt,desc") String sort,@AuthenticationPrincipal Jwt jwt) {
        return service.history(Actor.from(jwt),page,size,sort);
    }
    @PostMapping("/{id}/refund") ResponseEntity<Refund> refund(@PathVariable UUID id,@Valid @RequestBody RefundRequest body,
        @RequestHeader("Idempotency-Key") String key,@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.accepted().body(service.refund(id,body,key,Actor.from(jwt)));
    }
    @PostMapping("/{id}/refunds/{refundId}/retry") ResponseEntity<Refund> retryRefund(@PathVariable UUID id,@PathVariable UUID refundId,
        @Valid @RequestBody RefundRetryRequest body,@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().body(service.retryRefund(id,refundId,body.confirmNoRefundAtProvider(),Actor.from(jwt)));
    }
    @PostMapping("/{id}/refunds/{refundId}/resolve") ResponseEntity<Refund> resolveRefund(@PathVariable UUID id,@PathVariable UUID refundId,
        @Valid @RequestBody RefundResolutionRequest body,@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(service.resolveUncertainRefund(id,refundId,body,Actor.from(jwt)));
    }
    @PostMapping("/{id}/booking-claim/release") ResponseEntity<Void> releaseBookingClaim(@PathVariable UUID id,
        @Valid @RequestBody BookingClaimReleaseRequest body,@AuthenticationPrincipal Jwt jwt) {
        service.releaseFailedBookingClaim(id,body,Actor.from(jwt));
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/{id}/recovery-audit") List<RecoveryAudit> recoveryAudit(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) {
        return service.recoveryAudit(id,Actor.from(jwt));
    }
    @GetMapping("/{id}/refunds/{refundId}") Refund refundStatus(@PathVariable UUID id,@PathVariable UUID refundId,@AuthenticationPrincipal Jwt jwt) {
        return service.refundView(refundId,id,Actor.from(jwt));
    }
}
