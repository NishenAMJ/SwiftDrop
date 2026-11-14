package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.dto.PricingRequest;
import com.swiftdrop.parcel_pricing_service.dto.PricingResponse;
import com.swiftdrop.parcel_pricing_service.dto.PricingRuleRequest;
import com.swiftdrop.parcel_pricing_service.dto.PricingRuleResponse;
import com.swiftdrop.parcel_pricing_service.service.PricingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    private final PricingService pricingService;

    public PricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    /**
     * Calculate delivery fee based on parcel weight, delivery zone, and configured pricing rules.
     */
    @PostMapping("/calculate")
    public ResponseEntity<PricingResponse> calculateFee(@Valid @RequestBody PricingRequest request) {
        PricingResponse response = pricingService.calculateFee(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve all pricing rules. Accessible to Administrators / Dispatchers.
     */
    @GetMapping("/rules")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    public ResponseEntity<List<PricingRuleResponse>> getAllRules() {
        List<PricingRuleResponse> rules = pricingService.getAllPricingRules();
        return ResponseEntity.ok(rules);
    }

    /**
     * Retrieve a specific pricing rule by ID. Accessible to Administrators / Dispatchers.
     */
    @GetMapping("/rules/{id}")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    public ResponseEntity<PricingRuleResponse> getRuleById(@PathVariable Long id) {
        PricingRuleResponse rule = pricingService.getPricingRuleById(id);
        return ResponseEntity.ok(rule);
    }

    /**
     * Create a new pricing rule. Restricted to Administrators / Dispatchers.
     */
    @PostMapping("/rules")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    public ResponseEntity<PricingRuleResponse> createRule(@Valid @RequestBody PricingRuleRequest request) {
        PricingRuleResponse created = pricingService.createPricingRule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Update an existing pricing rule. Restricted to Administrators / Dispatchers.
     */
    @PutMapping("/rules/{id}")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    public ResponseEntity<PricingRuleResponse> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody PricingRuleRequest request) {
        PricingRuleResponse updated = pricingService.updatePricingRule(id, request);
        return ResponseEntity.ok(updated);
    }
}
