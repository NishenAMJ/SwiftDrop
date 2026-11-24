package com.swiftdrop.parcel_pricing_service.dto;

import com.swiftdrop.parcel_pricing_service.entity.BookingReceipt;
import com.swiftdrop.parcel_pricing_service.entity.Parcel;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Full booking details. Only ever returned to the customer who made the booking. */
public record BookingResponseDto(
    UUID id,
    String trackingNumber,
    UUID customerId,
    String description,
    BigDecimal weightKg,
    String urgency,
    String status,
    BigDecimal estimatedFee,
    OffsetDateTime createdAt,
    String receiptNumber,
    String paymentMethod,
    String paymentStatus,
    OffsetDateTime pricingExpiresAt,
    OffsetDateTime statusUpdatedAt,
    FeeBreakdownDto feeBreakdown,
    AddressDto pickupAddress,
    AddressDto deliveryAddress
) {
    public static BookingResponseDto from(Parcel parcel, BookingReceipt receipt) {
        return new BookingResponseDto(
            parcel.getId(),
            parcel.getTrackingNumber(),
            parcel.getCustomerId(),
            parcel.getDescription(),
            parcel.getWeightKg(),
            parcel.getUrgency(),
            parcel.getStatus(),
            parcel.getEstimatedFee(),
            parcel.getCreatedAt(),
            receipt != null ? receipt.getReceiptNumber() : null,
            parcel.getPaymentMethod(),
            parcel.getPaymentStatus(),
            parcel.getPricingExpiresAt(),
            parcel.getStatusUpdatedAt(),
            FeeBreakdownDto.from(parcel),
            AddressDto.from(parcel.getPickupAddress()),
            AddressDto.from(parcel.getDeliveryAddress())
        );
    }
}
