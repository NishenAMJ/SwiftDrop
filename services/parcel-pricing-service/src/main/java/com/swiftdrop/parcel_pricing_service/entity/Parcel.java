package com.swiftdrop.parcel_pricing_service.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "parcels", schema = "parcel_pricing")
public class Parcel {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tracking_number", unique = true, length = 32)
    private String trackingNumber;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "weight_kg", precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "urgency", nullable = false, length = 16)
    private String urgency = "NORMAL";

    @Column(name = "status", nullable = false, length = 30)
    private String status = "BOOKED";

    @Column(name = "estimated_fee", precision = 10, scale = 2)
    private BigDecimal estimatedFee;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "parcel", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ParcelAddress> addresses = new ArrayList<>();

    public Parcel() {
        this.id = UUID.randomUUID();
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public String getUrgency() {
        return urgency;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getEstimatedFee() {
        return estimatedFee;
    }

    public void setEstimatedFee(BigDecimal estimatedFee) {
        this.estimatedFee = estimatedFee;
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

    public List<ParcelAddress> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<ParcelAddress> addresses) {
        this.addresses = addresses;
    }

    public void addAddress(ParcelAddress address) {
        addresses.add(address);
        address.setParcel(this);
    }

    public void removeAddress(ParcelAddress address) {
        addresses.remove(address);
        address.setParcel(null);
    }
}

