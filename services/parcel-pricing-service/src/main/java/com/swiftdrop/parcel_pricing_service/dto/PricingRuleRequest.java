package com.swiftdrop.parcel_pricing_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PricingRuleRequest(
    @NotBlank(message = "Rule name is required")
    @Size(max = 100, message = "Rule name cannot exceed 100 characters")
    String ruleName,

    @NotBlank(message = "Rule code is required")
    @Size(max = 50, message = "Rule code cannot exceed 50 characters")
    String ruleCode,

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    String description,

    @NotNull(message = "Base fee is required")
    @DecimalMin(value = "0.00", message = "Base fee cannot be negative")
    BigDecimal baseFee,

    @NotNull(message = "Rate per kg is required")
    @DecimalMin(value = "0.00", message = "Rate per kg cannot be negative")
    BigDecimal ratePerKg,

    @NotNull(message = "Default zone charge is required")
    @DecimalMin(value = "0.00", message = "Default zone charge cannot be negative")
    BigDecimal defaultZoneCharge,

    @DecimalMin(value = "0.00", message = "Urgent surcharge cannot be negative")
    BigDecimal urgentSurcharge,

    Boolean urgentSurchargeEnabled,

    @DecimalMin(value = "0.01", message = "Minimum weight must be at least 0.01 kg")
    BigDecimal minWeightKg,

    @DecimalMin(value = "0.01", message = "Maximum weight must be at least 0.01 kg")
    BigDecimal maxWeightKg,

    Boolean active,

    Boolean isDefault
) {
}
