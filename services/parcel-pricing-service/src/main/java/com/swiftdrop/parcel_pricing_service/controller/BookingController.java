package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.dto.*;
import com.swiftdrop.parcel_pricing_service.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private static final UUID DEMO_CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponseDto> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID customerId = resolveCustomerId(jwt);
        BookingResponseDto response = bookingService.createBooking(request, customerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my")
    public ResponseEntity<List<BookingSummaryDto>> getMyBookings(@AuthenticationPrincipal Jwt jwt) {
        UUID customerId = resolveCustomerId(jwt);
        List<BookingSummaryDto> bookings = bookingService.getCustomerBookings(customerId);
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponseDto> getBookingById(@PathVariable UUID id) {
        return ResponseEntity.ok(bookingService.getBookingById(id));
    }

    @GetMapping("/track/{trackingNumber}")
    public ResponseEntity<BookingResponseDto> getBookingByTrackingNumber(@PathVariable String trackingNumber) {
        return ResponseEntity.ok(bookingService.getBookingByTrackingNumber(trackingNumber));
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<BookingReceiptDto> getReceipt(@PathVariable UUID id) {
        return ResponseEntity.ok(bookingService.getReceiptByParcelId(id));
    }

    @GetMapping("/{id}/payment-context")
    public ResponseEntity<PaymentContextResponse> getPaymentContext(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        PaymentContextResponse response = bookingService.getPaymentContext(id, jwt);
        return ResponseEntity.ok(response);
    }

    private UUID resolveCustomerId(Jwt jwt) {
        if (jwt != null && jwt.getSubject() != null) {
            try {
                return UUID.fromString(jwt.getSubject());
            } catch (IllegalArgumentException ignored) {
                // If subject is not standard UUID, fall back
            }
        }
        return DEMO_CUSTOMER_ID;
    }
}

