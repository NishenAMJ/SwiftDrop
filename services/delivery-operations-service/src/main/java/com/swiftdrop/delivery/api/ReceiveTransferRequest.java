package com.swiftdrop.delivery.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ReceiveTransferRequest(
        Instant receivedAt,
        @NotBlank @Size(max = 120) String receivedBy,
        @Size(max = 500) String notes
) {
}
