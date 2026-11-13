package com.swiftdrop.parcel_pricing_service.dto;

import com.swiftdrop.parcel_pricing_service.entity.Parcel;
import com.swiftdrop.parcel_pricing_service.entity.ParcelAddress;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

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
    String destinationCity
) {
    public static BookingSummaryDto from(Parcel parcel) {
        String recipient = null;
        String city = null;

        if (parcel.getAddresses() != null) {
            for (ParcelAddress addr : parcel.getAddresses()) {
                if ("DELIVERY".equalsIgnoreCase(addr.getAddressType())) {
                    recipient = addr.getContactName();
                    city = addr.getCity();
                    break;
                }
            }
        }

        return new BookingSummaryDto(
            parcel.getId(),
            parcel.getTrackingNumber(),
            parcel.getDescription(),
            parcel.getWeightKg(),
            parcel.getUrgency(),
            parcel.getStatus(),
            parcel.getEstimatedFee(),
            parcel.getCreatedAt(),
            recipient,
            city
        );
    }
}

