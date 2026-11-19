package com.swiftdrop.parcel_pricing_service.dto;

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
}

