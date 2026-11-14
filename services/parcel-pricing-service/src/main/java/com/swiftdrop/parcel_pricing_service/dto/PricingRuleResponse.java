package com.swiftdrop.parcel_pricing_service.dto;

import com.swiftdrop.parcel_pricing_service.entity.PricingRule;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PricingRuleResponse(
    Long id,
    String ruleName,
    String ruleCode,
    String description,
    BigDecimal baseFee,
    BigDecimal ratePerKg,
    BigDecimal defaultZoneCharge,
    BigDecimal urgentSurcharge,
    boolean urgentSurchargeEnabled,
    BigDecimal minWeightKg,
    BigDecimal maxWeightKg,
    boolean active,
    boolean isDefault,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static PricingRuleResponse from(PricingRule rule) {
        if (rule == null) return null;
        return new PricingRuleResponse(
            rule.getId(),
            rule.getRuleName(),
            rule.getRuleCode(),
            rule.getDescription(),
            rule.getBaseFee(),
            rule.getRatePerKg(),
            rule.getDefaultZoneCharge(),
            rule.getUrgentSurcharge(),
            rule.isUrgentSurchargeEnabled(),
            rule.getMinWeightKg(),
            rule.getMaxWeightKg(),
            rule.isActive(),
            rule.isDefault(),
            rule.getCreatedAt(),
            rule.getUpdatedAt()
        );
    }
}
