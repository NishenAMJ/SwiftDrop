package com.swiftdrop.parcel_pricing_service.dto;

import com.swiftdrop.parcel_pricing_service.entity.BookingReceipt;
import com.swiftdrop.parcel_pricing_service.entity.OrderSource;
import com.swiftdrop.parcel_pricing_service.entity.Parcel;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A merchant order as returned to its merchant. It is a parcel, so {@code id} is the booking id used
 * by every booking endpoint (receipt, payment, cancellation) and {@code trackingNumber} is the
 * public SWD reference.
 */
public record MerchantOrderResponse(
        UUID id,
        String trackingNumber,
        UUID merchantId,
        String merchantReference,
        String description,
        BigDecimal weightKg,
        String urgency,
        String urgentNote,
        String paymentMethod,
        String paymentStatus,
        BigDecimal codAmount,
        BigDecimal deliveryFee,
        String currency,
        FeeBreakdownDto feeBreakdown,
        String status,
        OrderSource source,
        String receiptNumber,
        OffsetDateTime pricingExpiresAt,
        AddressDto pickupAddress,
        AddressDto deliveryAddress,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static MerchantOrderResponse from(Parcel parcel, BookingReceipt receipt) {
        return new MerchantOrderResponse(
                parcel.getId(),
                parcel.getTrackingNumber(),
                parcel.getMerchantId(),
                parcel.getMerchantReference(),
                parcel.getDescription(),
                parcel.getWeightKg(),
                parcel.getUrgency(),
                parcel.getUrgentNote(),
                parcel.getPaymentMethod(),
                parcel.getPaymentStatus(),
                parcel.getCodAmount(),
                parcel.getEstimatedFee(),
                "LKR",
                FeeBreakdownDto.from(parcel),
                parcel.getStatus(),
                parcel.getOrderSource(),
                receipt != null ? receipt.getReceiptNumber() : null,
                parcel.getPricingExpiresAt(),
                AddressDto.from(parcel.getPickupAddress()),
                AddressDto.from(parcel.getDeliveryAddress()),
                parcel.getCreatedAt(),
                parcel.getUpdatedAt());
    }
}
