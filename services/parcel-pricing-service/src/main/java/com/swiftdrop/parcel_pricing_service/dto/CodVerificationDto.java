package com.swiftdrop.parcel_pricing_service.dto;

import com.swiftdrop.parcel_pricing_service.entity.Parcel;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The authoritative Cash on Delivery facts of a booking, for Payment & Settlement to check a
 * delivery.cod-collected event against. Money and ids only: no addresses, contacts or notes.
 */
public record CodVerificationDto(
        UUID bookingId,
        String trackingNumber,
        UUID merchantId,
        String paymentMethod,
        BigDecimal codAmount,
        String pricingSnapshotId,
        String currency,
        UUID customerId,
        String channel,
        String status
) {
    public static CodVerificationDto from(Parcel parcel) {
        return new CodVerificationDto(
                parcel.getId(),
                parcel.getTrackingNumber(),
                parcel.getMerchantId(),
                parcel.getPaymentMethod(),
                "COD".equals(parcel.getPaymentMethod())
                        ? ("DIRECT_WEBSITE".equals(parcel.getChannel()) && parcel.getMerchantId() == null
                            ? parcel.getEstimatedFee() : parcel.getCodAmount()) : null,
                parcel.getPricingSnapshotId(),
                "LKR", parcel.getCustomerId(), parcel.getChannel(), parcel.getStatus());
    }
}
