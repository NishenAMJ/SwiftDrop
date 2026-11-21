package com.swiftdrop.parcel_pricing_service.dto;

import com.swiftdrop.parcel_pricing_service.entity.Parcel;

import java.math.BigDecimal;

/** How the booking price was calculated by the pricing module. Null parts mean an older booking. */
public record FeeBreakdownDto(
    BigDecimal baseFee,
    BigDecimal weightCharge,
    BigDecimal zoneCharge,
    String zoneCode,
    BigDecimal urgentSurcharge,
    BigDecimal total,
    String pricingRuleCode
) {
    public static FeeBreakdownDto from(Parcel parcel) {
        return new FeeBreakdownDto(
            parcel.getBaseFee(),
            parcel.getWeightCharge(),
            parcel.getZoneCharge(),
            parcel.getPricingZoneCode(),
            parcel.getUrgentSurcharge(),
            parcel.getEstimatedFee(),
            parcel.getPricingRuleCode()
        );
    }
}
