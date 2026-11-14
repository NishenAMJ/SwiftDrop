package com.swiftdrop.parcel_pricing_service.dto;

import com.swiftdrop.parcel_pricing_service.entity.ZoneType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating and updating a delivery zone.
 * Status is not part of this request; use {@link ZoneStatusRequest} for that.
 */
public record ZoneRequest(

        @NotBlank(message = "Zone name is required")
        @Size(min = 2, max = 100, message = "Zone name must be between 2 and 100 characters")
        @Pattern(regexp = "^[\\p{L}\\p{N} .,'&()/-]*$",
                message = "Zone name contains unsupported characters")
        String name,

        @NotBlank(message = "Zone code is required")
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{1,19}$",
                message = "Zone code must be 2-20 characters: letters, digits, '_' or '-'")
        String code,

        @Size(max = 500, message = "Description must be at most 500 characters")
        String description,

        /** Optional; defaults to GENERAL. */
        ZoneType zoneType
) {
}
