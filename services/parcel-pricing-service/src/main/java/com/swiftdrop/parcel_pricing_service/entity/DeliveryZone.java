package com.swiftdrop.parcel_pricing_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A delivery zone. The pricing module references zones by {@link #getId()}
 * (or {@link #getCode()}) when calculating delivery charges.
 */
@Entity
@Table(name = "delivery_zones")
public class DeliveryZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** Unique, stored upper-case. */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", nullable = false, length = 20)
    private ZoneType zoneType = ZoneType.GENERAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ZoneStatus status = ZoneStatus.ACTIVE;

    @Column(name = "base_charge", precision = 10, scale = 2)
    private BigDecimal baseCharge = new BigDecimal("150.00");

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeliveryZone() {
        // required by JPA
    }

    public DeliveryZone(String name, String code, String description, ZoneType zoneType) {
        this.name = name;
        this.code = code;
        this.description = description;
        this.zoneType = zoneType;
        this.status = ZoneStatus.ACTIVE;
    }

    public DeliveryZone(String name, String code, String description, BigDecimal baseCharge,
                        ZoneStatus status, String classification, String coveredAreas) {
        this.name = name;
        this.code = code != null ? code.trim().toUpperCase() : null;
        this.description = description;
        this.baseCharge = baseCharge != null ? baseCharge : new BigDecimal("150.00");
        this.status = status != null ? status : ZoneStatus.ACTIVE;
        this.zoneType = ZoneType.DISTRICT;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ZoneType getZoneType() {
        return zoneType;
    }

    public void setZoneType(ZoneType zoneType) {
        this.zoneType = zoneType;
    }

    public ZoneStatus getStatus() {
        return status;
    }

    public void setStatus(ZoneStatus status) {
        this.status = status;
    }

    public BigDecimal getBaseCharge() {
        return baseCharge;
    }

    public void setBaseCharge(BigDecimal baseCharge) {
        this.baseCharge = baseCharge;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
