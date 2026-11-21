package com.swiftdrop.delivery.courier.api;

import com.swiftdrop.delivery.courier.domain.ProofOfDelivery;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProofResponse(
        UUID id,
        UUID assignmentId,
        String parcelReference,
        String recipientName,
        String notes,
        String mediaUrl,
        Instant deliveredAt,
        BigDecimal collectedAmount
) {
    public static ProofResponse from(ProofOfDelivery proof) {
        return new ProofResponse(proof.getId(), proof.getAssignment().getId(), proof.getParcelReference(),
                proof.getRecipientName(), proof.getNotes(), proof.getMediaUrl(), proof.getDeliveredAt(),
                proof.getCollectedAmount());
    }
}
