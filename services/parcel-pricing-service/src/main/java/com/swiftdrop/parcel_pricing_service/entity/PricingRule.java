package com.swiftdrop.parcel_pricing_service.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "pricing_rules", schema = "parcel_pricing")
public class PricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_name", nullable = false, length = 100)
    private String ruleName;

    @Column(name = "rule_code", nullable = false, unique = true, length = 50)
    private String ruleCode;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "base_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFee = new BigDecimal("300.00");

    @Column(name = "rate_per_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal ratePerKg = new BigDecimal("50.00");

    @Column(name = "default_zone_charge", nullable = false, precision = 10, scale = 2)
    private BigDecimal defaultZoneCharge = new BigDecimal("150.00");

    @Column(name = "urgent_surcharge", nullable = false, precision = 10, scale = 2)
    private BigDecimal urgentSurcharge = new BigDecimal("200.00");

    @Column(name = "urgent_surcharge_enabled", nullable = false)
    private boolean urgentSurchargeEnabled = false;

    @Column(name = "min_weight_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal minWeightKg = new BigDecimal("0.01");

    @Column(name = "max_weight_kg", precision = 6, scale = 2)
    private BigDecimal maxWeightKg = new BigDecimal("50.00");

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public PricingRule() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public PricingRule(String ruleName, String ruleCode, String description, BigDecimal baseFee,
                       BigDecimal ratePerKg, BigDecimal defaultZoneCharge, BigDecimal urgentSurcharge,
                       boolean urgentSurchargeEnabled, BigDecimal minWeightKg, BigDecimal maxWeightKg,
                       boolean active, boolean isDefault) {
        this();
        this.ruleName = ruleName;
        this.ruleCode = ruleCode != null ? ruleCode.trim().toUpperCase() : null;
        this.description = description;
        this.baseFee = baseFee != null ? baseFee : new BigDecimal("300.00");
        this.ratePerKg = ratePerKg != null ? ratePerKg : new BigDecimal("50.00");
        this.defaultZoneCharge = defaultZoneCharge != null ? defaultZoneCharge : new BigDecimal("150.00");
        this.urgentSurcharge = urgentSurcharge != null ? urgentSurcharge : new BigDecimal("200.00");
        this.urgentSurchargeEnabled = urgentSurchargeEnabled;
        this.minWeightKg = minWeightKg != null ? minWeightKg : new BigDecimal("0.01");
        this.maxWeightKg = maxWeightKg;
        this.active = active;
        this.isDefault = isDefault;
    }

    @PrePersist
    public void onPrePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.ruleCode != null) {
            this.ruleCode = this.ruleCode.trim().toUpperCase();
        }
        if (this.baseFee == null) {
            this.baseFee = new BigDecimal("300.00");
        }
        if (this.ratePerKg == null) {
            this.ratePerKg = new BigDecimal("50.00");
        }
        if (this.defaultZoneCharge == null) {
            this.defaultZoneCharge = new BigDecimal("150.00");
        }
        if (this.urgentSurcharge == null) {
            this.urgentSurcharge = new BigDecimal("200.00");
        }
    }

    @PreUpdate
    public void onPreUpdate() {
        this.updatedAt = OffsetDateTime.now();
        if (this.ruleCode != null) {
            this.ruleCode = this.ruleCode.trim().toUpperCase();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode != null ? ruleCode.trim().toUpperCase() : null;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getBaseFee() {
        return baseFee;
    }

    public void setBaseFee(BigDecimal baseFee) {
        this.baseFee = baseFee;
    }

    public BigDecimal getRatePerKg() {
        return ratePerKg;
    }

    public void setRatePerKg(BigDecimal ratePerKg) {
        this.ratePerKg = ratePerKg;
    }

    public BigDecimal getDefaultZoneCharge() {
        return defaultZoneCharge;
    }

    public void setDefaultZoneCharge(BigDecimal defaultZoneCharge) {
        this.defaultZoneCharge = defaultZoneCharge;
    }

    public BigDecimal getUrgentSurcharge() {
        return urgentSurcharge;
    }

    public void setUrgentSurcharge(BigDecimal urgentSurcharge) {
        this.urgentSurcharge = urgentSurcharge;
    }

    public boolean isUrgentSurchargeEnabled() {
        return urgentSurchargeEnabled;
    }

    public void setUrgentSurchargeEnabled(boolean urgentSurchargeEnabled) {
        this.urgentSurchargeEnabled = urgentSurchargeEnabled;
    }

    public BigDecimal getMinWeightKg() {
        return minWeightKg;
    }

    public void setMinWeightKg(BigDecimal minWeightKg) {
        this.minWeightKg = minWeightKg;
    }

    public BigDecimal getMaxWeightKg() {
        return maxWeightKg;
    }

    public void setMaxWeightKg(BigDecimal maxWeightKg) {
        this.maxWeightKg = maxWeightKg;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
