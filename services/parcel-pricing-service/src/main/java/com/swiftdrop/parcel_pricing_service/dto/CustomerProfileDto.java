package com.swiftdrop.parcel_pricing_service.dto;

import com.swiftdrop.parcel_pricing_service.client.IdentityClient.IdentityUser;
import com.swiftdrop.parcel_pricing_service.entity.CustomerProfile;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerProfileDto(
    UUID customerId,
    String fullName,
    String email,
    String phoneNumber,
    String streetAddress,
    String city,
    String postalCode,
    String avatarBase64,
    OffsetDateTime updatedAt
) {
    public static CustomerProfileDto from(CustomerProfile profile) {
        if (profile == null) return null;
        return new CustomerProfileDto(
            profile.getCustomerId(),
            profile.getFullName(),
            profile.getEmail(),
            profile.getPhoneNumber(),
            profile.getStreetAddress(),
            profile.getCity(),
            profile.getPostalCode(),
            profile.getAvatarBase64(),
            profile.getUpdatedAt()
        );
    }

    /** Account fields from Identity, address and photo from this service. */
    public static CustomerProfileDto of(UUID customerId, IdentityUser account, CustomerProfile local) {
        return new CustomerProfileDto(
            customerId,
            account.fullName(),
            account.email(),
            account.phoneNumber(),
            local != null ? local.getStreetAddress() : null,
            local != null ? local.getCity() : null,
            local != null ? local.getPostalCode() : null,
            local != null ? local.getAvatarBase64() : null,
            local != null ? local.getUpdatedAt() : null
        );
    }

    public static CustomerProfileDto empty(UUID customerId) {
        return new CustomerProfileDto(customerId, null, null, null, null, null, null, null, null);
    }
}
