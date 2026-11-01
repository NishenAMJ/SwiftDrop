package com.swiftdrop.identity.merchant;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.swiftdrop.identity.approval.ApplicationStatus;
import com.swiftdrop.identity.approval.LastDecision;

/** The {@code Merchant} schema from the contract. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MerchantResponse(
        UUID id,
        UUID userId,
        String businessName,
        BusinessType businessType,
        String businessRegistrationNumber,
        String contactEmail,
        String contactPhone,
        AddressDto businessAddress,
        ApplicationStatus status,
        LastDecision lastDecision,
        Instant submittedAt,
        Instant createdAt) {

    /** Full view, for dispatchers. */
    public static MerchantResponse from(Merchant m) {
        return new MerchantResponse(m.getId(), m.getUserId(), m.getBusinessName(), m.getBusinessType(),
                m.getBusinessRegistrationNumber(), m.getContactEmail(), m.getContactPhone(),
                m.getBusinessAddress().toDto(), m.getStatus(), m.lastDecision(), m.getSubmittedAt(),
                m.getCreatedAt());
    }

    /** The applicant's own view: hides which dispatcher decided. */
    public MerchantResponse forApplicant() {
        return new MerchantResponse(id, userId, businessName, businessType, businessRegistrationNumber, contactEmail,
                contactPhone, businessAddress, status, lastDecision == null ? null : lastDecision.withoutDecider(),
                submittedAt, createdAt);
    }
}
