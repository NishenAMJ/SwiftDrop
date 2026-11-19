package com.swiftdrop.parcel_pricing_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PricingRequest(
    @NotNull(message = "Parcel weight is required")
    @DecimalMin(value = "0.01", message = "Parcel weight must be greater than 0 kg")
    BigDecimal weightKg,

    String zoneCode,

    String ruleCode,

    Boolean isUrgent,

    @DecimalMin(value = "0.00", message = "Custom zone charge cannot be negative")
    BigDecimal customZoneCharge
) {
    public PricingRequest(BigDecimal weightKg, String zoneCode) {
        this(weightKg, zoneCode, null, false, null);
    }

    public PricingRequest(BigDecimal weightKg, String zoneCode, String ruleCode) {
        this(weightKg, zoneCode, ruleCode, false, null);
    }
}
