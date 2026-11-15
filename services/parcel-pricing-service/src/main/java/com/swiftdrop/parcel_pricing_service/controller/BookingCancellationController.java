package com.swiftdrop.parcel_pricing_service.controller;
import com.swiftdrop.parcel_pricing_service.dto.CancellationEligibilityDto;
import com.swiftdrop.parcel_pricing_service.service.BookingCancellationService;
import com.swiftdrop.parcel_pricing_service.common.ApiException;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.*;
@RestController
@RequestMapping("/api/v1/bookings/{id}")
public class BookingCancellationController {
    private final BookingCancellationService service;
    public BookingCancellationController(BookingCancellationService service) { this.service=service; }
    @GetMapping("/cancellation-eligibility")
    public CancellationEligibilityDto eligibility(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) { return service.eligibility(id,jwt); }
    @PostMapping("/cancel")
    public void cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) { service.initiate(id,jwt); }
    @GetMapping("/cancellation-status")
    public Map<String,Object> status(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        var p=service.owned(id,jwt);
        return Map.of("bookingId",id,"bookingStatus",p.getStatus(),"cancellationStatus",p.getCancellationStatus()==null?"NONE":p.getCancellationStatus(),"completed",false);
    }
    @GetMapping("/cancellation-receipt")
    public void receipt(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        service.owned(id,jwt);
        throw ApiException.conflict("RECEIPT_UNAVAILABLE","No verified completed cancellation receipt is available.");
    }
}
