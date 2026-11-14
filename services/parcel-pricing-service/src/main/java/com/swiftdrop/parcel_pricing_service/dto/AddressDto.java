package com.swiftdrop.parcel_pricing_service.dto;

import com.swiftdrop.parcel_pricing_service.entity.ParcelAddress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressDto(
    @NotBlank(message = "Contact name is required")
    @Size(max = 100, message = "Contact name cannot exceed 100 characters")
    String contactName,

    @NotBlank(message = "Contact phone is required")
    @Size(max = 25, message = "Contact phone cannot exceed 25 characters")
    String contactPhone,

    @NotBlank(message = "Street address is required")
    @Size(max = 255, message = "Street address cannot exceed 255 characters")
    String streetAddress,

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City cannot exceed 100 characters")
    String city,

    String postalCode,

    String zoneCode
) {
    public static AddressDto from(ParcelAddress address) {
        if (address == null) return null;
        return new AddressDto(
            address.getContactName(),
            address.getContactPhone(),
            address.getStreetAddress(),
            address.getCity(),
            address.getPostalCode(),
            address.getZoneCode()
        );
    }
}

