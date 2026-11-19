package com.swiftdrop.parcel_pricing_service.dto;

import com.swiftdrop.parcel_pricing_service.entity.ZoneStatus;
import jakarta.validation.constraints.NotNull;

/** Payload for {@code PATCH /api/zones/{id}/status}. */
public record ZoneStatusRequest(

        @NotNull(message = "Status is required (ACTIVE or INACTIVE)")
        ZoneStatus status
) {
}
