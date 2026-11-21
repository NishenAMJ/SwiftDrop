package com.swiftdrop.delivery.courier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "couriers")
public class Courier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Reference to the user in identity-merchant-service. Not a database foreign key. */
    @Column(name = "identity_user_id", nullable = false, unique = true, updatable = false)
    private UUID identityUserId;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(name = "vehicle_number", length = 30)
    private String vehicleNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private CourierCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourierStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Courier() {
    }

    public Courier(UUID identityUserId, String fullName, String phone, String vehicleNumber, CourierCategory category) {
        this.identityUserId = identityUserId;
        this.fullName = fullName;
        this.phone = phone;
        this.vehicleNumber = vehicleNumber;
        this.category = category;
        this.status = CourierStatus.AVAILABLE;
    }

    public void update(String fullName, String phone, String vehicleNumber, CourierCategory category) {
        this.fullName = fullName;
        this.phone = phone;
        this.vehicleNumber = vehicleNumber;
        this.category = category;
    }

    public void changeStatus(CourierStatus status) {
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
    public UUID getIdentityUserId() { return identityUserId; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getVehicleNumber() { return vehicleNumber; }
    public CourierCategory getCategory() { return category; }
    public CourierStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
