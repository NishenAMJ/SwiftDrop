package com.swiftdrop.parcel_pricing_service.dto;

import com.swiftdrop.parcel_pricing_service.entity.Parcel;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What Delivery Operations needs to assign a parcel and to report a Cash on Delivery collection.
 * Includes the pickup and delivery contacts, which the assigned courier needs to do the job, the booking status
 * (so a cancelled parcel is never scheduled) and the weight (for the courier category's limit). No fees.
 * Dispatcher-only: customers never reach this.
 */
public record DispatchParcelDto(
        UUID id,
        String trackingNumber,
        UUID customerId,
        UUID merchantId,
        String paymentMethod,
        BigDecimal codAmount,
        String pricingSnapshotId,
        String currency,
        AddressDto pickupAddress,
        AddressDto deliveryAddress,
        String status,
        BigDecimal weightKg
) {
    public static DispatchParcelDto from(Parcel parcel) {
        return new DispatchParcelDto(
                parcel.getId(),
                parcel.getTrackingNumber(),
                parcel.getCustomerId(),
                parcel.getMerchantId(),
                parcel.getPaymentMethod(),
                "COD".equals(parcel.getPaymentMethod())
                        ? ("DIRECT_WEBSITE".equals(parcel.getChannel()) && parcel.getMerchantId() == null
                            ? parcel.getEstimatedFee() : parcel.getCodAmount()) : null,
                parcel.getPricingSnapshotId(),
                "LKR",
                AddressDto.from(parcel.getPickupAddress()),
                AddressDto.from(parcel.getDeliveryAddress()),
                parcel.getStatus(),
                parcel.getWeightKg());
    }
}
