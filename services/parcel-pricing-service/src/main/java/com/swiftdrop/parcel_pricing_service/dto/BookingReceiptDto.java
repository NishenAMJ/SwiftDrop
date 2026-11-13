package com.swiftdrop.parcel_pricing_service.dto;

import com.swiftdrop.parcel_pricing_service.entity.BookingReceipt;
import com.swiftdrop.parcel_pricing_service.entity.Parcel;
import com.swiftdrop.parcel_pricing_service.entity.ParcelAddress;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BookingReceiptDto(
    String receiptNumber,
    UUID parcelId,
    String trackingNumber,
    OffsetDateTime issuedAt,
    BigDecimal totalAmount,
    String currency,
    String description,
    BigDecimal weightKg,
    String urgency,
    String status,
    AddressDto pickupAddress,
    AddressDto deliveryAddress
) {
    public static BookingReceiptDto from(BookingReceipt receipt) {
        if (receipt == null) return null;
        Parcel parcel = receipt.getParcel();
        AddressDto pickup = null;
        AddressDto delivery = null;

        if (parcel != null && parcel.getAddresses() != null) {
            for (ParcelAddress addr : parcel.getAddresses()) {
                if ("PICKUP".equalsIgnoreCase(addr.getAddressType())) {
                    pickup = AddressDto.from(addr);
                } else if ("DELIVERY".equalsIgnoreCase(addr.getAddressType())) {
                    delivery = AddressDto.from(addr);
                }
            }
        }

        return new BookingReceiptDto(
            receipt.getReceiptNumber(),
            parcel != null ? parcel.getId() : null,
            parcel != null ? parcel.getTrackingNumber() : null,
            receipt.getIssuedAt(),
            receipt.getTotalAmount(),
            receipt.getCurrency(),
            parcel != null ? parcel.getDescription() : null,
            parcel != null ? parcel.getWeightKg() : null,
            parcel != null ? parcel.getUrgency() : null,
            parcel != null ? parcel.getStatus() : null,
            pickup,
            delivery
        );
    }
}

