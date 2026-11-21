package com.swiftdrop.delivery.courier.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @param collectedAmount the cash handed over; required for Cash on Delivery parcels and refused for others
 */
public record RecordProofRequest(
        @NotBlank @Size(max = 120) String recipientName,
        @Size(max = 500) String notes,
        @Size(max = 500) String mediaUrl,
        Instant deliveredAt,
        @Positive @Digits(integer = 10, fraction = 2) @DecimalMax("9999999999.99") BigDecimal collectedAmount
) {
}
