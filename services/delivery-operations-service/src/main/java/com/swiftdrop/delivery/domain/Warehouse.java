package com.swiftdrop.delivery.domain;

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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "warehouses")
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "address_line_1", nullable = false, length = 160)
    private String addressLine1;

    @Column(name = "address_line_2", length = 160)
    private String addressLine2;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(nullable = false, length = 80)
    private String district;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "contact_name", nullable = false, length = 120)
    private String contactName;

    @Column(name = "contact_phone", nullable = false, length = 30)
    private String contactPhone;

    @Column(name = "contact_email", length = 160)
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WarehouseStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Warehouse() {
    }

    public Warehouse(String code, String name, String addressLine1, String addressLine2, String city,
                     String district, String postalCode, String contactName, String contactPhone,
                     String contactEmail) {
        this.code = code;
        this.name = name;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.district = district;
        this.postalCode = postalCode;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.contactEmail = contactEmail;
        this.status = WarehouseStatus.ACTIVE;
    }

    public void update(String name, String addressLine1, String addressLine2, String city, String district,
                       String postalCode, String contactName, String contactPhone, String contactEmail) {
        this.name = name;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.district = district;
        this.postalCode = postalCode;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.contactEmail = contactEmail;
    }

    public void changeStatus(WarehouseStatus status) {
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
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getCity() { return city; }
    public String getDistrict() { return district; }
    public String getPostalCode() { return postalCode; }
    public String getContactName() { return contactName; }
    public String getContactPhone() { return contactPhone; }
    public String getContactEmail() { return contactEmail; }
    public WarehouseStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
