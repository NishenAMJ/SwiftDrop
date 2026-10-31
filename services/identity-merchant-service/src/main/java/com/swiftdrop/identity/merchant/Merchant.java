package com.swiftdrop.identity.merchant;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.swiftdrop.identity.approval.Application;

@Entity
@Table(name = "merchants")
public class Merchant extends Application {

    @Id
    @Column(name = "merchant_id")
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "business_name", nullable = false, length = 120)
    private String businessName;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false, length = 30)
    private BusinessType businessType;

    @Column(name = "business_registration_number", length = 50)
    private String businessRegistrationNumber;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false, length = 16)
    private String contactPhone;

    @Embedded
    private BusinessAddress businessAddress;

    protected Merchant() {
    }

    Merchant(UUID userId, MerchantApplicationRequest request, Instant now) {
        super(now);
        this.id = UUID.randomUUID();
        this.userId = userId;
        setDetails(request);
    }

    /** Applicant replaces the details when resubmitting after a rejection. */
    void replaceDetails(MerchantApplicationRequest request, Instant now) {
        setDetails(request);
        touch(now);
    }

    private void setDetails(MerchantApplicationRequest request) {
        this.businessName = request.businessName().trim();
        this.businessType = request.businessType();
        this.businessRegistrationNumber = blankToNull(request.businessRegistrationNumber());
        this.contactEmail = request.contactEmail() == null ? null
                : request.contactEmail().trim().toLowerCase(Locale.ROOT);
        this.contactPhone = request.contactPhone();
        this.businessAddress = BusinessAddress.from(request.businessAddress());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getBusinessName() {
        return businessName;
    }

    BusinessType getBusinessType() {
        return businessType;
    }

    String getBusinessRegistrationNumber() {
        return businessRegistrationNumber;
    }

    String getContactEmail() {
        return contactEmail;
    }

    String getContactPhone() {
        return contactPhone;
    }

    BusinessAddress getBusinessAddress() {
        return businessAddress;
    }
}
