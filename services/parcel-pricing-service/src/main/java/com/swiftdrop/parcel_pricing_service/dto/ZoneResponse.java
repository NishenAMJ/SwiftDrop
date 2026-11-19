package com.swiftdrop.parcel_pricing_service.dto;

import com.swiftdrop.parcel_pricing_service.entity.DeliveryZone;
import com.swiftdrop.parcel_pricing_service.entity.ZoneStatus;
import com.swiftdrop.parcel_pricing_service.entity.ZoneType;

import java.time.Instant;

/** Delivery zone as returned by the API. The JPA entity is never exposed directly. */
public record ZoneResponse(
        Long id,
        String name,
        String code,
        String description,
        ZoneType zoneType,
        ZoneStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static ZoneResponse from(DeliveryZone zone) {
        return new ZoneResponse(
                zone.getId(),
                zone.getName(),
                zone.getCode(),
                zone.getDescription(),
                zone.getZoneType(),
                zone.getStatus(),
                zone.getCreatedAt(),
                zone.getUpdatedAt());
    }
}
