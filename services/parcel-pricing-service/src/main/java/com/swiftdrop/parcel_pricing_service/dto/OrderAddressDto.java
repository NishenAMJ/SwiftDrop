package com.swiftdrop.parcel_pricing_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Pickup or delivery address of a merchant order.
 * (Defined for this module because the fields of the existing AddressDto were not
 * available when this module was written; swap them if the two are compatible.)
 */
public record OrderAddressDto(

        @NotBlank(message = "Address line 1 is required")
        @Size(max = 150, message = "Address line 1 must be at most 150 characters")
        String addressLine1,

        @Size(max = 150, message = "Address line 2 must be at most 150 characters")
        String addressLine2,

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must be at most 100 characters")
        String city,

        @Size(max = 100, message = "District must be at most 100 characters")
        String district,

        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 -]{2,9}$", message = "Postal code is not valid")
        String postalCode
) {
}
