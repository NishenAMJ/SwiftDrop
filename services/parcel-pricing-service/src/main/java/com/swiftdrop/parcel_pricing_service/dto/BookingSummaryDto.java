package com.swiftdrop.parcel_pricing_service.dto;

import com.swiftdrop.parcel_pricing_service.entity.BookingStatus;
import com.swiftdrop.parcel_pricing_service.entity.Parcel;
import com.swiftdrop.parcel_pricing_service.entity.ParcelAddress;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One row of booking history. */
public record BookingSummaryDto(
    UUID id,
    String trackingNumber,
    String description,
    BigDecimal weightKg,
    String urgency,
    String status,
    BigDecimal estimatedFee,
    OffsetDateTime createdAt,
    String recipientName,
    String destinationCity,
    String paymentMethod,
    String paymentStatus,
    boolean cancellable
) {
    public static BookingSummaryDto from(Parcel parcel) {
        ParcelAddress delivery = parcel.getDeliveryAddress();
        return new BookingSummaryDto(
            parcel.getId(),
            parcel.getTrackingNumber(),
            parcel.getDescription(),
            parcel.getWeightKg(),
            parcel.getUrgency(),
            parcel.getStatus(),
            parcel.getEstimatedFee(),
            parcel.getCreatedAt(),
            delivery != null ? delivery.getContactName() : null,
            delivery != null ? delivery.getCity() : null,
            parcel.getPaymentMethod(),
            parcel.getPaymentStatus(),
            // Cancellation is allowed only until a courier is scheduled (see BookingCancellationService).
            BookingStatus.BOOKED.equals(parcel.getStatus())
        );
    }
}
