package com.swiftdrop.payment.ledger.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public final class MerchantLedgerDtos {
    private MerchantLedgerDtos() { }

    public record Adjustment(
        @NotNull UUID merchantId,
        @NotNull @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotBlank @Size(max = 200) String sourceReference,
        @NotBlank @Size(max = 500) String reason
    ) { }

    public record Entry(
        UUID id,
        UUID merchantId,
        BigDecimal amount,
        String currency,
        String entryType,
        String sourceReference,
        String idempotencyKey,
        String reason,
        UUID settlementId,
        boolean settlementEligible,
        LocalDateTime createdAt
    ) { }

    public record Page(
        List<Entry> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        BigDecimal balance,
        String currency
    ) { }
}
