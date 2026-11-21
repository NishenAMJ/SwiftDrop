package com.swiftdrop.delivery.courier.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record RecordProofRequest(
        @NotBlank @Size(max = 120) String recipientName,
        @Size(max = 500) String notes,
        @Size(max = 500) String mediaUrl,
        Instant deliveredAt
) {
}
