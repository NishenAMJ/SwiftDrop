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
        @NotNull LocalDateTime cutoffAt
    ) { }

    public record Approve(
        @AssertTrue boolean confirmCalculatedAmount,
        @Size(min = 1, max = 500) String note
    ) { }

    public record RecordPayment(
        @NotBlank @Size(max = 200) String externalReference,
        @NotNull LocalDateTime paidAt,
        @AssertTrue boolean confirmExternalPayment
    ) { }

    public record View(
        UUID id,
        UUID merchantId,
        List<UUID> ledgerEntryIds,
        BigDecimal amount,
        String currency,
        LocalDateTime cutoffAt,
        String status,
        UUID calculatedBy,
        LocalDateTime calculatedAt,
        UUID approvedBy,
        LocalDateTime approvedAt,
        String approvalNote,
        UUID paidBy,
        LocalDateTime paidAt,
        String externalPaymentReference
    ) { }
}
