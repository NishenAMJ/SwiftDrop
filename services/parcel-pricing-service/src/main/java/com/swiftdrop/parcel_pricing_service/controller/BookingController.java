package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.client.BookingPaymentVerifier;
import com.swiftdrop.parcel_pricing_service.common.ApiException;
import com.swiftdrop.parcel_pricing_service.dto.*;
import com.swiftdrop.parcel_pricing_service.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Customer bookings. Every endpoint needs a login, and a customer only ever sees their own
 * bookings. Public tracking for receivers is provided by Tracking & Reporting
 * (/api/v1/public/tracking/{trackingNumber}), which receives our parcel.booked event.
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final BookingPaymentVerifier paymentVerifier;

    public BookingController(BookingService bookingService, BookingPaymentVerifier paymentVerifier) {
        this.bookingService = bookingService;
        this.paymentVerifier = paymentVerifier;
    }

    public record ConfirmPaymentRequest(UUID paymentId) {}

    /** Every registered account has the CUSTOMER role; dispatcher-only accounts cannot book. */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<BookingResponseDto> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(request, jwt));
    }

    @GetMapping("/my")
    public List<BookingSummaryDto> getMyBookings(@AuthenticationPrincipal Jwt jwt) {
        return bookingService.getCustomerBookings(customerId(jwt));
    }

    @GetMapping("/{id}")
    public BookingResponseDto getBookingById(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return bookingService.getOwnedBooking(id, customerId(jwt));
    }

    /** The booker's own booking by reference. Anyone else uses public tracking. */
    @GetMapping("/track/{trackingNumber}")
    public BookingResponseDto getBookingByTrackingNumber(@PathVariable String trackingNumber,
                                                         @AuthenticationPrincipal Jwt jwt) {
        return bookingService.getOwnedBookingByTrackingNumber(trackingNumber, customerId(jwt));
    }

    @GetMapping("/{id}/receipt")
    public BookingReceiptDto getReceipt(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return bookingService.getReceipt(id, customerId(jwt));
    }

    /** Called by Payment & Settlement with the customer's forwarded token. */
    @GetMapping("/{id}/payment-context")
    public PaymentContextResponse getPaymentContext(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return bookingService.getPaymentContext(id, jwt);
    }

    /** After returning from the payment page: verifies the payment with Payment & Settlement. */
    @PostMapping("/{id}/confirm-payment")
    public BookingResponseDto confirmPayment(@PathVariable UUID id, @RequestBody ConfirmPaymentRequest request,
                                             @AuthenticationPrincipal Jwt jwt) {
        UUID customerId = customerId(jwt);
        if (request == null || request.paymentId() == null) {
            throw ApiException.badRequest("PAYMENT_REQUIRED", "A payment reference is required.");
        }
        // Ownership is checked before calling Payment so nobody can probe other bookings.
        bookingService.getOwnedBooking(id, customerId);
        var payment = paymentVerifier.verify(request.paymentId(), jwt);
        return bookingService.confirmPaid(id, customerId, request.paymentId(), payment);
    }

    /** The customer leaves an unpaid checkout. Nothing is charged and it never appears in history. */
    @PostMapping("/{id}/discard")
    public ResponseEntity<Void> discardDraft(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        bookingService.discardDraft(id, customerId(jwt));
        return ResponseEntity.noContent().build();
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
