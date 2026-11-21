package com.swiftdrop.delivery.courier.parcel;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What delivery events need to know about a parcel.
 *
 * @param customerId        the registered customer who booked it; null for bookings without one
 * @param merchantId        the merchant whose order it is; null for customer bookings
 * @param codAmount         cash the courier collects from the receiver; null unless it is a merchant COD order
 * @param pricingSnapshotId Parcel & Pricing's immutable pricing snapshot, copied into the COD event
 * @param pickup            who and where to collect from; null if Parcel & Pricing sent none
 * @param delivery          who and where to deliver to; null if Parcel & Pricing sent none
 * @param status            the booking status in Parcel & Pricing, for example CANCELLED; null if unknown
 * @param weightKg          the parcel's declared weight; null if unknown
 */
public record ParcelRef(UUID parcelId, String trackingNumber, UUID customerId, UUID merchantId,
                        BigDecimal codAmount, String pricingSnapshotId, String currency,
                        ParcelContact pickup, ParcelContact delivery, String status, BigDecimal weightKg) {

    public ParcelRef(UUID parcelId, String trackingNumber, UUID customerId, UUID merchantId,
                     BigDecimal codAmount, String pricingSnapshotId, String currency,
                     ParcelContact pickup, ParcelContact delivery) {
        this(parcelId, trackingNumber, customerId, merchantId, codAmount, pricingSnapshotId, currency, pickup,
                delivery, null, null);
    }

    public ParcelRef(UUID parcelId, String trackingNumber, UUID customerId, UUID merchantId,
                     BigDecimal codAmount, String pricingSnapshotId, String currency) {
        this(parcelId, trackingNumber, customerId, merchantId, codAmount, pricingSnapshotId, currency, null, null, null, null);
    }

    public ParcelRef(UUID parcelId, String trackingNumber, UUID customerId) {
        this(parcelId, trackingNumber, customerId, null, null, null, null, null, null, null, null);
    }

    public boolean isCancelled() {
        return "CANCELLED".equalsIgnoreCase(status);
    }

    /** True when the courier must collect cash and Payment must be told about it. */
    public boolean collectsCash() {
        return merchantId != null && codAmount != null && codAmount.signum() > 0 && pricingSnapshotId != null;
    }
}
