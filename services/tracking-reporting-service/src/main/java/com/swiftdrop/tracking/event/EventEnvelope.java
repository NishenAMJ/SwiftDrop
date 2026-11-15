package com.swiftdrop.tracking.event;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record EventEnvelope(
        @NotNull UUID eventId,
        @NotBlank
        @Pattern(regexp = "^[a-z][a-z-]*\\.[a-z][a-z-]*$") String eventType,
        @Min(1) int version,
        @NotNull Instant occurredAt,
        @NotBlank String producer,
        @Valid @NotNull TrackingEventData data) {
}
