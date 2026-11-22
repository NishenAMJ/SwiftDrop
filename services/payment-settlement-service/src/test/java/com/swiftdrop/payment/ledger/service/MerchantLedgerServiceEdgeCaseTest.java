package com.swiftdrop.payment.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.payment.ledger.dto.MerchantLedgerDtos.Adjustment;
import com.swiftdrop.payment.ledger.entity.LedgerEntryType;
import com.swiftdrop.payment.ledger.entity.MerchantLedgerEntry;
import com.swiftdrop.payment.ledger.repository.MerchantLedgerEntryRepository;
import com.swiftdrop.payment.onlinepayment.service.Actor;
import com.swiftdrop.payment.onlinepayment.service.PaymentProblem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/** Ledger visibility, adjustment validation and idempotent replays. */
class MerchantLedgerServiceEdgeCaseTest {
    private final MerchantLedgerEntryRepository entries = mock(MerchantLedgerEntryRepository.class);
    private final MerchantLedgerService service = new MerchantLedgerService(entries);
    private final UUID merchantId = UUID.fromString("846c790e-497a-4340-8039-79e25c95f40e");
    private final Actor merchant = new Actor(UUID.randomUUID(), merchantId, Set.of("MERCHANT"), "test");
    private final Actor dispatcher = new Actor(UUID.randomUUID(), null, Set.of("DISPATCHER"), "test");
    private static final String KEY = "adjustment-key-001";

    private static void assertProblem(int status, Executable call) {
        assertThatThrownBy(call::execute).isInstanceOfSatisfying(PaymentProblem.class,
                problem -> assertThat(problem.status).isEqualTo(status));
    }

    private Adjustment adjustment(String amount) {
        return new Adjustment(merchantId, amount == null ? null : new BigDecimal(amount), "LKR", "case-123", "Correction");
    }

    @Test
    void listRequiresAMerchantForDispatchersAndRejectsOtherRoles() {
        assertProblem(400, () -> service.list(null, "LKR", 0, 20, dispatcher));
        assertProblem(403, () -> service.list(merchantId, "LKR", 0, 20, new Actor(UUID.randomUUID(), null, Set.of("COURIER"), "t")));
        assertProblem(403, () -> service.list(null, "LKR", 0, 20, new Actor(UUID.randomUUID(), null, Set.of("MERCHANT"), "t")));
        verifyNoInteractions(entries);
    }

    @Test
    void listValidatesCurrencyAndPagination() {
        assertProblem(400, () -> service.list(merchantId, "lkr", 0, 20, dispatcher));
        assertProblem(400, () -> service.list(merchantId, null, 0, 20, dispatcher));
        assertProblem(400, () -> service.list(merchantId, "LKR", -1, 20, dispatcher));
        assertProblem(400, () -> service.list(merchantId, "LKR", 0, 0, dispatcher));
        assertProblem(400, () -> service.list(merchantId, "LKR", 0, 101, dispatcher));
        verifyNoInteractions(entries);
    }

    @Test
    void dispatcherReadsTheRequestedMerchantWithDefaultPaging() {
        when(entries.findByMerchantIdAndCurrency(eq(merchantId), eq("LKR"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(entries.balance(merchantId, "LKR")).thenReturn(BigDecimal.ZERO);
        var page = service.list(merchantId, "LKR", null, null, dispatcher);
        assertThat(page.balance()).isEqualByComparingTo("0");
        verify(entries).findByMerchantIdAndCurrency(eq(merchantId), eq("LKR"),
                org.mockito.ArgumentMatchers.argThat(p -> p.getPageNumber() == 0 && p.getPageSize() == 20));
    }

    @Test
    void adjustmentsAreDispatcherOnlyAndFullyValidated() {
        assertProblem(403, () -> service.adjust(adjustment("10"), KEY, merchant));
        assertProblem(400, () -> service.adjust(adjustment("10"), "bad", dispatcher));
        assertProblem(400, () -> service.adjust(null, KEY, dispatcher));
        assertProblem(400, () -> service.adjust(adjustment(null), KEY, dispatcher));
        assertProblem(400, () -> service.adjust(adjustment("0"), KEY, dispatcher));
        assertProblem(400, () -> service.adjust(adjustment("1.001"), KEY, dispatcher));
        assertProblem(400, () -> service.adjust(adjustment("12345678901"), KEY, dispatcher));
        assertProblem(400, () -> service.adjust(new Adjustment(null, BigDecimal.TEN, "LKR", "case", "reason"), KEY, dispatcher));
        assertProblem(400, () -> service.adjust(new Adjustment(merchantId, BigDecimal.TEN, "lkr", "case", "reason"), KEY, dispatcher));
        assertProblem(400, () -> service.adjust(new Adjustment(merchantId, BigDecimal.TEN, "LKR", " ", "reason"), KEY, dispatcher));
        assertProblem(400, () -> service.adjust(new Adjustment(merchantId, BigDecimal.TEN, "LKR", "case", "r".repeat(501)), KEY, dispatcher));
        verifyNoInteractions(entries);
    }

    @Test
    void replayReturnsTheOriginalEntryAndRejectsChangedDetails() {
        MerchantLedgerEntry existing = new MerchantLedgerEntry();
        existing.setId(UUID.randomUUID());
        existing.setMerchantId(merchantId);
        existing.setAmount(new BigDecimal("-100.00"));
        existing.setCurrency("LKR");
        existing.setEntryType(LedgerEntryType.ADJUSTMENT);
        existing.setSourceReference("case-123");
        existing.setReason("Correction");
        existing.setCreatedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        when(entries.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existing));

        assertThat(service.adjust(new Adjustment(merchantId, new BigDecimal("-100"), "LKR", " case-123 ", " Correction "),
                KEY, dispatcher).id()).isEqualTo(existing.getId());
        assertProblem(409, () -> service.adjust(adjustment("-99.99"), KEY, dispatcher));
        assertProblem(409, () -> service.adjust(new Adjustment(UUID.randomUUID(), new BigDecimal("-100"), "LKR", "case-123", "Correction"),
                KEY, dispatcher));
        assertProblem(409, () -> service.adjust(new Adjustment(merchantId, new BigDecimal("-100"), "LKR", "case-999", "Correction"),
                KEY, dispatcher));
        assertProblem(409, () -> service.adjust(new Adjustment(merchantId, new BigDecimal("-100"), "LKR", "case-123", "Other"),
                KEY, dispatcher));

        existing.setEntryType(LedgerEntryType.COD_COLLECTION);
        assertProblem(409, () -> service.adjust(new Adjustment(merchantId, new BigDecimal("-100"), "LKR", "case-123", "Correction"),
                KEY, dispatcher));
        verify(entries, never()).save(any());
    }

    @Test
    void aSourceReferenceCanOnlyBeAdjustedOnce() {
        when(entries.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(entries.findByEntryTypeAndSourceReference(LedgerEntryType.ADJUSTMENT, "case-123"))
                .thenReturn(Optional.of(new MerchantLedgerEntry()));
        assertProblem(409, () -> service.adjust(adjustment("50"), KEY, dispatcher));
        verify(entries, never()).save(any());
    }
}
