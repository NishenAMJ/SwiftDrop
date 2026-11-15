package com.swiftdrop.parcel_pricing_service.dto;

import com.swiftdrop.parcel_pricing_service.entity.BookingReceipt;
import com.swiftdrop.parcel_pricing_service.entity.Parcel;
import com.swiftdrop.parcel_pricing_service.entity.ParcelAddress;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

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
    AddressDto pickupAddress,
    AddressDto deliveryAddress
) {
    public static BookingResponseDto from(Parcel parcel, BookingReceipt receipt) {
        AddressDto pickup = null;
        AddressDto delivery = null;

        if (parcel.getAddresses() != null) {
            for (ParcelAddress addr : parcel.getAddresses()) {
                if ("PICKUP".equalsIgnoreCase(addr.getAddressType())) {
                    pickup = AddressDto.from(addr);
                } else if ("DELIVERY".equalsIgnoreCase(addr.getAddressType())) {
                    delivery = AddressDto.from(addr);
                }
            }
        }

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
            pickup,
            delivery
        );
    }
}
