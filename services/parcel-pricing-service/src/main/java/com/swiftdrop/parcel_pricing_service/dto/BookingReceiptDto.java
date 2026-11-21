package com.swiftdrop.parcel_pricing_service.dto;

import com.swiftdrop.parcel_pricing_service.entity.BookingReceipt;
import com.swiftdrop.parcel_pricing_service.entity.Parcel;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The booking receipt required for Member 3: reference, customer details, addresses, weight,
 * delivery type, fee and booking date, plus the payment state and how the fee was calculated.
 */
public record BookingReceiptDto(
    String receiptNumber,
    UUID parcelId,
    String trackingNumber,
    OffsetDateTime bookingDate,
    OffsetDateTime issuedAt,
    CustomerDetails customer,
    BigDecimal totalAmount,
    String currency,
    FeeBreakdownDto feeBreakdown,
    String description,
    BigDecimal weightKg,
    String urgency,
    String status,
    String paymentMethod,
    String paymentStatus,
    OffsetDateTime paidAt,
    AddressDto pickupAddress,
    AddressDto deliveryAddress
) {
    public record CustomerDetails(String name, String email, String phone) {
    }

    public static BookingReceiptDto from(BookingReceipt receipt) {
        if (receipt == null) return null;
        Parcel parcel = receipt.getParcel();
        return new BookingReceiptDto(
            receipt.getReceiptNumber(),
            parcel.getId(),
            parcel.getTrackingNumber(),
            parcel.getCreatedAt(),
            receipt.getIssuedAt(),
            new CustomerDetails(parcel.getCustomerName(), parcel.getCustomerEmail(), parcel.getCustomerPhone()),
            receipt.getTotalAmount(),
            receipt.getCurrency(),
            FeeBreakdownDto.from(parcel),
            parcel.getDescription(),
            parcel.getWeightKg(),
            parcel.getUrgency(),
            parcel.getStatus(),
            parcel.getPaymentMethod(),
            parcel.getPaymentStatus(),
            parcel.getPaidAt(),
            AddressDto.from(parcel.getPickupAddress()),
            AddressDto.from(parcel.getDeliveryAddress())
        );
    }
}
