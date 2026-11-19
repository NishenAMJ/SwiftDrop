package com.swiftdrop.payment.settlement.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class MerchantSettlementDtos {
    private MerchantSettlementDtos() { }

    public record Calculate(
        @NotNull UUID merchantId,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotNull OffsetDateTime cutoffAt
    ) { }

    public record Approve(
        @AssertTrue boolean confirmCalculatedAmount,
        @Size(min = 1, max = 500) String note
    ) { }

    public record RecordPayment(
        @NotBlank @Size(max = 200) String externalReference,
        @NotNull OffsetDateTime paidAt,
        @AssertTrue boolean confirmExternalPayment
    ) { }

    public record View(
        UUID id,
        UUID merchantId,
        List<UUID> ledgerEntryIds,
        BigDecimal amount,
        String currency,
        OffsetDateTime cutoffAt,
        String status,
        UUID calculatedBy,
        OffsetDateTime calculatedAt,
        UUID approvedBy,
        OffsetDateTime approvedAt,
        String approvalNote,
        UUID paidBy,
        OffsetDateTime paidAt,
        String externalPaymentReference
    ) { }
}
