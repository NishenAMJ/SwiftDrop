package com.swiftdrop.parcel_pricing_service.dto;

import java.math.BigDecimal;

public record PricingResponse(
    BigDecimal totalDeliveryFee,
    BigDecimal baseFee,
    BigDecimal weightCharge,
    BigDecimal weightKg,
    BigDecimal ratePerKg,
    BigDecimal zoneCharge,
    String zoneCode,
    BigDecimal urgentSurcharge,
    boolean urgentSurchargeApplied,
    String currency,
    String appliedRuleCode,
    String breakdownDescription
) {
}
