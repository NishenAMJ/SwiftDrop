package com.swiftdrop.payment.cod.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CodCollectedEvent(
    UUID eventId,
    String eventType,
    int version,
    OffsetDateTime occurredAt,
    String producer,
    Data data
) {
    public record Data(
        String bookingId,
        String trackingNumber,
        String pricingSnapshotId,
        UUID merchantId,
        UUID courierId,
        UUID deliveryAttemptId,
        String paymentMethod,
        BigDecimal expectedAmount,
        BigDecimal collectedAmount,
        String currency,
        OffsetDateTime collectedAt
    ) { }
}
