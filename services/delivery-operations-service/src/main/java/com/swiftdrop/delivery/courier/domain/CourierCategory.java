package com.swiftdrop.delivery.courier.domain;

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
import java.util.UUID;

@Entity
@Table(name = "courier_categories")
public class CourierCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(name = "max_weight_kg", precision = 8, scale = 2)
    private BigDecimal maxWeightKg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoryStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourierCategory() {
    }

    public CourierCategory(String code, String name, String description, BigDecimal maxWeightKg) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.maxWeightKg = maxWeightKg;
        this.status = CategoryStatus.ACTIVE;
    }

    public void update(String name, String description, BigDecimal maxWeightKg) {
        this.name = name;
        this.description = description;
        this.maxWeightKg = maxWeightKg;
    }

    public void changeStatus(CategoryStatus status) {
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getMaxWeightKg() { return maxWeightKg; }
    public CategoryStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
