package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.dto.CustomerProfileDto;
import com.swiftdrop.parcel_pricing_service.dto.UpdateProfileRequest;
import com.swiftdrop.parcel_pricing_service.service.CustomerProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customer/profile")
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"})
public class CustomerProfileController {

    private static final UUID DEMO_CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final CustomerProfileService profileService;

    public CustomerProfileController(CustomerProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<CustomerProfileDto> getProfile(@AuthenticationPrincipal Jwt jwt) {
        UUID customerId = resolveCustomerId(jwt);
        return ResponseEntity.ok(profileService.getProfile(customerId));
    }

    @PutMapping
    public ResponseEntity<CustomerProfileDto> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID customerId = resolveCustomerId(jwt);
        return ResponseEntity.ok(profileService.updateProfile(customerId, request));
    }

    private UUID resolveCustomerId(Jwt jwt) {
        if (jwt != null && jwt.getSubject() != null) {
            try {
                return UUID.fromString(jwt.getSubject());
            } catch (IllegalArgumentException ignored) {
                // Not standard UUID, fall back to default
            }
        }
        return DEMO_CUSTOMER_ID;
    }
}

