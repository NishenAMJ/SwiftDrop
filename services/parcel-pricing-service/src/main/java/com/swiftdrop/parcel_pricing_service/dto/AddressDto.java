package com.swiftdrop.parcel_pricing_service.dto;

import com.swiftdrop.parcel_pricing_service.entity.ParcelAddress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressDto(
    @NotBlank(message = "Contact name is required")
    @Size(max = 100, message = "Contact name cannot exceed 100 characters")
    String contactName,

    // Sri Lankan local (0771234567) or international (+94771234567) numbers, spaces and dashes allowed.
    @NotBlank(message = "Contact phone is required")
    @Size(max = 25, message = "Contact phone cannot exceed 25 characters")
    @Pattern(regexp = "^\\+?[0-9][0-9 ()-]{8,23}$", message = "Enter a valid phone number, for example 0771234567 or +94771234567")
    String contactPhone,

    @NotBlank(message = "Street address is required")
    @Size(max = 255, message = "Street address cannot exceed 255 characters")
    String streetAddress,

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City cannot exceed 100 characters")
    String city,

    @Size(max = 20, message = "Postal code cannot exceed 20 characters")
    @Pattern(regexp = "^[A-Za-z0-9 -]*$", message = "Postal code can contain only letters, digits, spaces and dashes")
    String postalCode,

    @Size(max = 20, message = "Zone code cannot exceed 20 characters")
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
