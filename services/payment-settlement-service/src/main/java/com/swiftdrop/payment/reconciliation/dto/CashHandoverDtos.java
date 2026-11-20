package com.swiftdrop.payment.reconciliation.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

public final class CashHandoverDtos {
    private CashHandoverDtos() { }

    public record Create(
        @NotEmpty @Size(max = 500) List<@NotNull UUID> collectionIds,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 10, fraction = 2)
        BigDecimal submittedAmount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency
    ) { }

    public record Confirm(
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal receivedAmount,
        @Size(min = 1, max = 500) String investigationNote
    ) { }

    public record Reconciliation(
        UUID id,
        BigDecimal expectedAmount,
        BigDecimal receivedAmount,
        BigDecimal differenceAmount,
        String currency,
        String status,
        String investigationNote,
        UUID confirmedBy,
        OffsetDateTime confirmedAt
    ) { }

    public record Handover(
        UUID id,
        UUID courierId,
        List<UUID> collectionIds,
        BigDecimal expectedAmount,
        BigDecimal submittedAmount,
        String currency,
        String status,
        OffsetDateTime submittedAt,
        Reconciliation reconciliation
    ) { }

    public record Page(
        List<Handover> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) { }
}
