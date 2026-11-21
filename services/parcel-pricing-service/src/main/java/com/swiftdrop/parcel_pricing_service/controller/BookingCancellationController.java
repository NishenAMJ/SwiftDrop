package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.dto.CancellationEligibilityDto;
import com.swiftdrop.parcel_pricing_service.dto.CancellationResultDto;
import com.swiftdrop.parcel_pricing_service.service.BookingCancellationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Customer cancellation before a courier is scheduled. All endpoints are owner-only. */
@RestController
@RequestMapping("/api/v1/bookings/{id}")
public class BookingCancellationController {

    private final BookingCancellationService service;

    public BookingCancellationController(BookingCancellationService service) {
        this.service = service;
    }

    @GetMapping("/cancellation-eligibility")
    public CancellationEligibilityDto eligibility(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.eligibility(id, jwt);
    }

    @PostMapping("/cancel")
    public CancellationResultDto cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.initiate(id, jwt);
    }

    @GetMapping("/cancellation-status")
    public CancellationResultDto status(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.status(id, jwt);
    }

    @GetMapping("/cancellation-receipt")
    public CancellationResultDto receipt(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.receipt(id, jwt);
    }
}
