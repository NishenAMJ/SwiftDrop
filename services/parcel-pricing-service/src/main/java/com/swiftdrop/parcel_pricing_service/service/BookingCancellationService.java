package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.common.ApiException;
import com.swiftdrop.parcel_pricing_service.dto.BookingResponseDto;
import com.swiftdrop.parcel_pricing_service.dto.CancellationEligibilityDto;
import com.swiftdrop.parcel_pricing_service.dto.CancellationResultDto;
import com.swiftdrop.parcel_pricing_service.entity.BookingStatus;
import com.swiftdrop.parcel_pricing_service.entity.Parcel;
import com.swiftdrop.parcel_pricing_service.entity.PaymentMethod;
import com.swiftdrop.parcel_pricing_service.event.ParcelEventPublisher;
import com.swiftdrop.parcel_pricing_service.repository.ParcelRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Customer cancellation rule: a booking can be cancelled while it is BOOKED, which means no
 * courier has been scheduled to pick it up yet. Once Delivery Operations assigns a courier
 * (status ASSIGNED or later) the parcel is on its way and cancellation is closed.
 *
 * <p>COD: nothing was paid, so nothing is charged. ONLINE: the payment minus the configured
 * cancellation fee (LKR 0 by default) is refunded to the original payment method. Refunds are
 * performed by Payment & Settlement, which receives the parcel.cancelled event.
 */
@Service
@Transactional
public class BookingCancellationService {
    @Value("${swiftdrop.bookings.online-payments-enabled:false}")
    private boolean onlinePaymentsEnabled;

    private final ParcelRepository parcels;
    private final ParcelEventPublisher events;
    private final BigDecimal onlineCancellationFee;

    public BookingCancellationService(ParcelRepository parcels, ParcelEventPublisher events,
                                      @Value("${swiftdrop.bookings.cancellation-fee:0.00}") BigDecimal onlineCancellationFee) {
        this.parcels = parcels;
        this.events = events;
        this.onlineCancellationFee = onlineCancellationFee.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public Parcel owned(UUID id, Jwt jwt) {
        UUID customer = BookingService.customerIdOf(jwt);
        Parcel parcel = parcels.findById(id).orElseThrow(() -> ApiException.notFound("Booking was not found."));
        if (!customer.equals(parcel.getCustomerId())) throw ApiException.forbidden("You do not own this booking.");
        return parcel;
    }

    @Transactional(readOnly = true)
    public CancellationEligibilityDto eligibility(UUID id, Jwt jwt) {
        Parcel parcel = owned(id, jwt);
        String reason = ineligibleReason(parcel);
        boolean eligible = reason == null;
        boolean online = PaymentMethod.ONLINE.name().equals(parcel.getPaymentMethod());
        BigDecimal paid = online && BookingService.isPaid(parcel) ? parcel.getEstimatedFee() : null;
        BigDecimal fee = feeFor(parcel);
        BigDecimal refundable = paid == null ? null : paid.subtract(fee).max(BigDecimal.ZERO);
        return new CancellationEligibilityDto(eligible,
                eligible ? (online ? "No courier has been scheduled yet. Your payment will be refunded"
                        + (fee.signum() > 0 ? " after the cancellation fee." : " in full.")
                        : "No courier has been scheduled yet. Nothing is charged for a cash on delivery booking.")
                        : reason,
                fee, null, id, parcel.getStatus(), null, paid, refundable,
                parcel.getPaymentMethod(), parcel.getPaymentStatus(), BookingResponseDto.from(parcel, null));
    }

    /** Safe to repeat: cancelling an already cancelled booking returns the same result. */
    public CancellationResultDto initiate(UUID id, Jwt jwt) {
        UUID customer = BookingService.customerIdOf(jwt);
        Parcel parcel = parcels.lockById(id).orElseThrow(() -> ApiException.notFound("Booking was not found."));
        if (!customer.equals(parcel.getCustomerId())) throw ApiException.forbidden("You do not own this booking.");
        if (BookingStatus.CANCELLED.equals(parcel.getStatus())) return CancellationResultDto.from(parcel);

        String reason = ineligibleReason(parcel);
        if (reason != null) throw ApiException.conflict("CANCELLATION_NOT_ALLOWED", reason);

        boolean online = PaymentMethod.ONLINE.name().equals(parcel.getPaymentMethod());
        BigDecimal fee = feeFor(parcel);
        BigDecimal refund = online ? parcel.getEstimatedFee().subtract(fee).max(BigDecimal.ZERO) : BigDecimal.ZERO;

        parcel.setStatus(BookingStatus.CANCELLED);
        parcel.setCancelledAt(OffsetDateTime.now());
        parcel.setCancellationFee(fee);
        parcel.setRefundAmount(refund.setScale(2, RoundingMode.HALF_UP));
        parcel.setCancellationStatus(refund.signum() > 0 ? "REFUND_PENDING" : "COMPLETED");
        if (!online) parcel.setPaymentStatus("NOT_REQUIRED");
        parcel.setStatusUpdatedAt(OffsetDateTime.now());
        parcel.setUpdatedAt(OffsetDateTime.now());

        events.bookingCancelled(parcel);
        return CancellationResultDto.from(parcel);
    }

    @Transactional(readOnly = true)
    public CancellationResultDto status(UUID id, Jwt jwt) {
        return CancellationResultDto.from(owned(id, jwt));
    }

    @Transactional(readOnly = true)
    public CancellationResultDto receipt(UUID id, Jwt jwt) {
        Parcel parcel = owned(id, jwt);
        if (!BookingStatus.CANCELLED.equals(parcel.getStatus())) {
            throw ApiException.conflict("NOT_CANCELLED", "This booking has not been cancelled.");
        }
        return CancellationResultDto.from(parcel);
    }

    private BigDecimal feeFor(Parcel parcel) {
        if (!PaymentMethod.ONLINE.name().equals(parcel.getPaymentMethod()) || parcel.getEstimatedFee() == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return onlineCancellationFee.min(parcel.getEstimatedFee());
    }

    /** @return null when the booking can be cancelled, otherwise a reason for the customer. */
    private String ineligibleReason(Parcel parcel) {
        String status = parcel.getStatus();
        if (BookingStatus.CANCELLED.equals(status)) return "This booking is already cancelled.";
        if (PaymentMethod.ONLINE.name().equals(parcel.getPaymentMethod()) && !onlinePaymentsEnabled)
            return "Online refunds are currently unavailable. Contact support about cancelling this paid booking.";
        if (BookingStatus.UNPAID_DRAFTS.contains(status) || !BookingService.isVisibleBooking(parcel)) {
            return "This booking was never confirmed, so there is nothing to cancel.";
        }
        if (BookingStatus.DELIVERED.equals(status)) return "This parcel has already been delivered.";
        if (!BookingStatus.BOOKED.equals(status)) {
            return "A courier has already been scheduled for this parcel, so it can no longer be cancelled.";
        }
        return null;
    }
}
