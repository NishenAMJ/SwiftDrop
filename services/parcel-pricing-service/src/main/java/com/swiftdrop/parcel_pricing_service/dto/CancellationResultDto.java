package com.swiftdrop.parcel_pricing_service.dto;

import com.swiftdrop.parcel_pricing_service.entity.Parcel;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Outcome of a cancellation, also used as the cancellation receipt.
 * cancellationStatus: COMPLETED (nothing to refund), REFUND_PENDING (refund requested from
 * Payment & Settlement) or REFUNDED (Payment confirmed the refund).
 */
public record CancellationResultDto(
    UUID bookingId,
    String trackingNumber,
    String bookingStatus,
    String cancellationStatus,
    String paymentMethod,
    String paymentStatus,
    BigDecimal originalAmount,
    BigDecimal cancellationFee,
    BigDecimal refundAmount,
    OffsetDateTime cancelledAt,
    boolean completed
) {
    public static CancellationResultDto from(Parcel parcel) {
        return new CancellationResultDto(
            parcel.getId(),
            parcel.getTrackingNumber(),
            parcel.getStatus(),
            parcel.getCancellationStatus() == null ? "NONE" : parcel.getCancellationStatus(),
            parcel.getPaymentMethod(),
            parcel.getPaymentStatus(),
            parcel.getEstimatedFee(),
            parcel.getCancellationFee(),
            parcel.getRefundAmount(),
            parcel.getCancelledAt(),
            "CANCELLED".equals(parcel.getStatus())
        );
    }
}
