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

    private final BookingService bookingService;
    private final com.swiftdrop.parcel_pricing_service.client.BookingPaymentVerifier paymentVerifier;

    public BookingController(BookingService bookingService, com.swiftdrop.parcel_pricing_service.client.BookingPaymentVerifier paymentVerifier) {
        this.bookingService = bookingService;
        this.paymentVerifier = paymentVerifier;
    }

    public record ConfirmPaymentRequest(UUID paymentId) {}

    @PostMapping("/{id}/confirm-payment")
    public BookingResponseDto confirmPayment(@PathVariable UUID id, @RequestBody ConfirmPaymentRequest request,
                                             @AuthenticationPrincipal Jwt jwt) {
        UUID customerId = resolveCustomerId(jwt);
        requireOwner(id, jwt);
        if (request.paymentId() == null) throw com.swiftdrop.parcel_pricing_service.common.ApiException.badRequest("PAYMENT_REQUIRED", "A payment reference is required.");
        return bookingService.confirmPaid(id, customerId, paymentVerifier.verify(request.paymentId(), jwt));
    }

    private void requireOwner(UUID id, Jwt jwt) {
        if (!resolveCustomerId(jwt).equals(bookingService.getBookingById(id).customerId()))
            throw com.swiftdrop.parcel_pricing_service.common.ApiException.forbidden("You do not own this booking.");
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
    public ResponseEntity<BookingResponseDto> getBookingById(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        requireOwner(id, jwt);
        return ResponseEntity.ok(bookingService.getBookingById(id));
    }

    @GetMapping("/track/{trackingNumber}")
    public ResponseEntity<BookingResponseDto> getBookingByTrackingNumber(@PathVariable String trackingNumber) {
        return ResponseEntity.ok(bookingService.getBookingByTrackingNumber(trackingNumber));
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<BookingReceiptDto> getReceipt(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        requireOwner(id, jwt);
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
        throw com.swiftdrop.parcel_pricing_service.common.ApiException.unauthorized("A valid customer login is required.");
    }
}

