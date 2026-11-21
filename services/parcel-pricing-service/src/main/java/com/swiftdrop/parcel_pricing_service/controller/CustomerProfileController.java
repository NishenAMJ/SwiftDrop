package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.common.ApiException;
import com.swiftdrop.parcel_pricing_service.dto.CustomerProfileDto;
import com.swiftdrop.parcel_pricing_service.dto.UpdateProfileRequest;
import com.swiftdrop.parcel_pricing_service.service.CustomerProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** The signed-in customer's own profile. CORS is configured once in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/customer/profile")
public class CustomerProfileController {

    private final CustomerProfileService profileService;

    public CustomerProfileController(CustomerProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public CustomerProfileDto getProfile(@AuthenticationPrincipal Jwt jwt) {
        return profileService.getProfile(customerId(jwt), jwt);
    }

    @PutMapping
    public CustomerProfileDto updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                            @AuthenticationPrincipal Jwt jwt) {
        return profileService.updateProfile(customerId(jwt), request, jwt);
    }

    private static UUID customerId(Jwt jwt) {
        if (jwt != null && jwt.getSubject() != null) {
            try {
                return UUID.fromString(jwt.getSubject());
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        throw ApiException.unauthorized("A valid customer login is required.");
    }
}
