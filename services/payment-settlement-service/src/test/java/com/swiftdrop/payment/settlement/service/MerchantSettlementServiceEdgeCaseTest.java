package com.swiftdrop.payment.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.payment.ledger.entity.MerchantLedgerEntry;
import com.swiftdrop.payment.ledger.repository.MerchantLedgerEntryRepository;
import com.swiftdrop.payment.onlinepayment.service.Actor;
import com.swiftdrop.payment.onlinepayment.service.PaymentProblem;
import com.swiftdrop.payment.settlement.dto.MerchantSettlementDtos.Approve;
import com.swiftdrop.payment.settlement.dto.MerchantSettlementDtos.Calculate;
import com.swiftdrop.payment.settlement.dto.MerchantSettlementDtos.RecordPayment;
import com.swiftdrop.payment.settlement.entity.MerchantSettlement;
import com.swiftdrop.payment.settlement.entity.SettlementStatus;
import com.swiftdrop.payment.settlement.repository.MerchantSettlementEntryRepository;
import com.swiftdrop.payment.settlement.repository.MerchantSettlementRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** Duplicate requests, wrong amounts and invalid state transitions for merchant settlements. */
class MerchantSettlementServiceEdgeCaseTest {
    private final MerchantSettlementRepository settlements = mock(MerchantSettlementRepository.class);
    private final MerchantSettlementEntryRepository settlementEntries = mock(MerchantSettlementEntryRepository.class);
    private final MerchantLedgerEntryRepository ledger = mock(MerchantLedgerEntryRepository.class);
    private final MerchantSettlementService service = new MerchantSettlementService(settlements, settlementEntries, ledger);

    private final UUID merchantId = UUID.fromString("846c790e-497a-4340-8039-79e25c95f40e");
    private final Actor dispatcher = new Actor(UUID.randomUUID(), null, Set.of("DISPATCHER"), "test");
    private final Actor merchant = new Actor(UUID.randomUUID(), merchantId, Set.of("MERCHANT"), "test");
    private static final String KEY = "settlement-key-0001";
    private final OffsetDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusHours(1).withNano(0).atOffset(ZoneOffset.UTC);

    private static void assertProblem(int status, Executable call) {
        assertThatThrownBy(call::execute).isInstanceOfSatisfying(PaymentProblem.class,
                problem -> assertThat(problem.status).isEqualTo(status));
    }

    private MerchantSettlement settlement(SettlementStatus status) {
        MerchantSettlement settlement = new MerchantSettlement();
        settlement.setId(UUID.randomUUID());
        settlement.setMerchantId(merchantId);
        settlement.setAmount(new BigDecimal("900.00"));
        settlement.setCurrency("LKR");
        settlement.setCutoffAt(cutoff.toLocalDateTime());
        settlement.setStatus(status);
        settlement.setApprovedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(30));
        return settlement;
    }

    private MerchantLedgerEntry entry(String amount) {
        MerchantLedgerEntry entry = new MerchantLedgerEntry();
        entry.setId(UUID.randomUUID());
        entry.setMerchantId(merchantId);
        entry.setAmount(new BigDecimal(amount));
        entry.setCurrency("LKR");
        return entry;
    }

    // ---------- calculate ----------

    @Test
    void calculationIsDispatcherOnlyAndValidatesTheRequest() {
        assertProblem(403, () -> service.calculate(new Calculate(merchantId, "LKR", cutoff), KEY, merchant));
        assertProblem(400, () -> service.calculate(new Calculate(merchantId, "LKR", cutoff), "short", dispatcher));
        assertProblem(400, () -> service.calculate(null, KEY, dispatcher));
        assertProblem(400, () -> service.calculate(new Calculate(null, "LKR", cutoff), KEY, dispatcher));
        assertProblem(400, () -> service.calculate(new Calculate(merchantId, "lkr", cutoff), KEY, dispatcher));
        assertProblem(400, () -> service.calculate(new Calculate(merchantId, null, cutoff), KEY, dispatcher));
        assertProblem(400, () -> service.calculate(new Calculate(merchantId, "LKR", null), KEY, dispatcher));
        assertProblem(400, () -> service.calculate(new Calculate(merchantId, "LKR", OffsetDateTime.now().plusHours(1)), KEY, dispatcher));
        verifyNoInteractions(settlements, ledger);
    }

    @Test
    void duplicateCalculationReturnsTheFrozenSettlementWithoutRecalculating() {
        MerchantSettlement existing = settlement(SettlementStatus.CALCULATED);
        when(settlements.findByCalculatedByAndIdempotencyKey(dispatcher.id(), KEY)).thenReturn(Optional.of(existing));

        var replay = service.calculate(new Calculate(merchantId, "LKR", cutoff), KEY, dispatcher);

        assertThat(replay.id()).isEqualTo(existing.getId());
        verify(ledger, never()).findUnsettledThrough(any(), any(), any());
        verify(settlements, never()).saveAndFlush(any());
    }

    @Test
    void reusingACalculationKeyForAnotherMerchantCurrencyOrCutoffIsAConflict() {
        MerchantSettlement existing = settlement(SettlementStatus.CALCULATED);
        when(settlements.findByCalculatedByAndIdempotencyKey(dispatcher.id(), KEY)).thenReturn(Optional.of(existing));

        assertProblem(409, () -> service.calculate(new Calculate(UUID.randomUUID(), "LKR", cutoff), KEY, dispatcher));
        assertProblem(409, () -> service.calculate(new Calculate(merchantId, "USD", cutoff), KEY, dispatcher));
        assertProblem(409, () -> service.calculate(new Calculate(merchantId, "LKR", cutoff.minusMinutes(1)), KEY, dispatcher));
    }

    @Test
    void calculationRefusesEmptyOrNonPositiveBalances() {
        when(settlements.findByCalculatedByAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(ledger.findUnsettledThrough(any(), any(), any())).thenReturn(List.of());
        assertProblem(409, () -> service.calculate(new Calculate(merchantId, "LKR", cutoff), KEY, dispatcher));

        when(ledger.findUnsettledThrough(any(), any(), any())).thenReturn(List.of(entry("100.00"), entry("-100.00")));
        assertProblem(409, () -> service.calculate(new Calculate(merchantId, "LKR", cutoff), KEY, dispatcher));

        when(ledger.findUnsettledThrough(any(), any(), any())).thenReturn(List.of(entry("-50.00")));
        assertProblem(409, () -> service.calculate(new Calculate(merchantId, "LKR", cutoff), KEY, dispatcher));
        verify(settlements, never()).saveAndFlush(any());
    }

    @Test
    void calculationRefusesTotalsBeyondTheSupportedAmount() {
        when(settlements.findByCalculatedByAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(ledger.findUnsettledThrough(any(), any(), any()))
                .thenReturn(List.of(entry("9999999999.99"), entry("0.01")));
        assertProblem(400, () -> service.calculate(new Calculate(merchantId, "LKR", cutoff), KEY, dispatcher));
    }

    // ---------- get ----------

    @Test
    void getIsLimitedToDispatchersAndTheOwningMerchant() {
        MerchantSettlement settlement = settlement(SettlementStatus.CALCULATED);
        when(settlements.findById(settlement.getId())).thenReturn(Optional.of(settlement));
        when(settlementEntries.findBySettlementId(settlement.getId())).thenReturn(List.of());

        assertThat(service.get(settlement.getId(), dispatcher).amount()).isEqualByComparingTo("900.00");
        assertThat(service.get(settlement.getId(), merchant).merchantId()).isEqualTo(merchantId);
        assertProblem(403, () -> service.get(settlement.getId(),
                new Actor(UUID.randomUUID(), UUID.randomUUID(), Set.of("MERCHANT"), "test")));
        assertProblem(403, () -> service.get(settlement.getId(), new Actor(UUID.randomUUID(), null, Set.of("CUSTOMER"), "test")));
        assertProblem(404, () -> service.get(UUID.randomUUID(), dispatcher));
    }

    // ---------- approve ----------

    @Test
    void approvalRequiresConfirmationAndAShortNote() {
        UUID id = UUID.randomUUID();
        assertProblem(403, () -> service.approve(id, new Approve(true, null), KEY, merchant));
        assertProblem(400, () -> service.approve(id, null, KEY, dispatcher));
        assertProblem(400, () -> service.approve(id, new Approve(false, null), KEY, dispatcher));
        assertProblem(400, () -> service.approve(id, new Approve(true, "n".repeat(501)), KEY, dispatcher));
        verifyNoInteractions(settlements);
    }

    @Test
    void duplicateApprovalIsReplayedButCannotBeReusedForAnotherSettlement() {
        MerchantSettlement approved = settlement(SettlementStatus.APPROVED);
        approved.setApprovalNote("Checked");
        when(settlements.findByApprovedByAndApprovalIdempotencyKey(dispatcher.id(), KEY)).thenReturn(Optional.of(approved));

        assertThat(service.approve(approved.getId(), new Approve(true, " Checked "), KEY, dispatcher).status()).isEqualTo("APPROVED");
        assertProblem(409, () -> service.approve(UUID.randomUUID(), new Approve(true, "Checked"), KEY, dispatcher));
        assertProblem(409, () -> service.approve(approved.getId(), new Approve(true, "Other"), KEY, dispatcher));
        verify(settlements, never()).lock(any());
    }

    @Test
    void onlyCalculatedSettlementsCanBeApproved() {
        when(settlements.findByApprovedByAndApprovalIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        UUID missing = UUID.randomUUID();
        when(settlements.lock(missing)).thenReturn(Optional.empty());
        assertProblem(404, () -> service.approve(missing, new Approve(true, null), KEY, dispatcher));

        for (SettlementStatus status : List.of(SettlementStatus.APPROVED, SettlementStatus.PAID)) {
            MerchantSettlement settlement = settlement(status);
            when(settlements.lock(settlement.getId())).thenReturn(Optional.of(settlement));
            assertProblem(409, () -> service.approve(settlement.getId(), new Approve(true, null), KEY, dispatcher));
        }
        verify(settlements, never()).save(any());
    }

    @Test
    void approvalRecordsTheApproverAndTrimmedNote() {
        MerchantSettlement settlement = settlement(SettlementStatus.CALCULATED);
        settlement.setApprovedAt(null);
        when(settlements.findByApprovedByAndApprovalIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(settlements.lock(settlement.getId())).thenReturn(Optional.of(settlement));
        when(settlements.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var view = service.approve(settlement.getId(), new Approve(true, "  Verified ledger  "), KEY, dispatcher);

        assertThat(view.status()).isEqualTo("APPROVED");
        assertThat(view.approvedBy()).isEqualTo(dispatcher.id());
        assertThat(view.approvalNote()).isEqualTo("Verified ledger");
        assertThat(settlement.getApprovalIdempotencyKey()).isEqualTo(KEY);
    }

    // ---------- record payment ----------

    @Test
    void paymentEvidenceIsValidated() {
        UUID id = UUID.randomUUID();
        OffsetDateTime paidAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        assertProblem(400, () -> service.recordPayment(id, null, KEY, dispatcher));
        assertProblem(400, () -> service.recordPayment(id, new RecordPayment("bank-1", paidAt, false), KEY, dispatcher));
        assertProblem(400, () -> service.recordPayment(id, new RecordPayment("bank-1", null, true), KEY, dispatcher));
        assertProblem(400, () -> service.recordPayment(id, new RecordPayment(" ", paidAt, true), KEY, dispatcher));
        assertProblem(400, () -> service.recordPayment(id, new RecordPayment(null, paidAt, true), KEY, dispatcher));
        assertProblem(400, () -> service.recordPayment(id, new RecordPayment("r".repeat(201), paidAt, true), KEY, dispatcher));
        assertProblem(403, () -> service.recordPayment(id, new RecordPayment("bank-1", paidAt, true), KEY, merchant));
        verifyNoInteractions(settlements, ledger);
    }

    @Test
    void duplicatePaymentRecordingIsReplayedWithoutASecondLedgerEntry() {
        MerchantSettlement paid = settlement(SettlementStatus.PAID);
        LocalDateTime paidAt = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5).withNano(0);
        paid.setPaidAt(paidAt);
        paid.setExternalPaymentReference("bank-123");
        when(settlements.findByPaidByAndPaymentIdempotencyKey(dispatcher.id(), KEY)).thenReturn(Optional.of(paid));

        var replay = service.recordPayment(paid.getId(), new RecordPayment(" bank-123 ", paidAt.atOffset(ZoneOffset.UTC), true),
                KEY, dispatcher);

        assertThat(replay.status()).isEqualTo("PAID");
        assertProblem(409, () -> service.recordPayment(paid.getId(),
                new RecordPayment("bank-999", paidAt.atOffset(ZoneOffset.UTC), true), KEY, dispatcher));
        assertProblem(409, () -> service.recordPayment(paid.getId(),
                new RecordPayment("bank-123", paidAt.plusSeconds(1).atOffset(ZoneOffset.UTC), true), KEY, dispatcher));
        assertProblem(409, () -> service.recordPayment(UUID.randomUUID(),
                new RecordPayment("bank-123", paidAt.atOffset(ZoneOffset.UTC), true), KEY, dispatcher));
        verify(ledger, never()).save(any());
    }

    @Test
    void onlyApprovedSettlementsCanBePaid() {
        when(settlements.findByPaidByAndPaymentIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        OffsetDateTime paidAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        UUID missing = UUID.randomUUID();
        when(settlements.lock(missing)).thenReturn(Optional.empty());
        assertProblem(404, () -> service.recordPayment(missing, new RecordPayment("bank-1", paidAt, true), KEY, dispatcher));

        for (SettlementStatus status : List.of(SettlementStatus.CALCULATED, SettlementStatus.PAID)) {
            MerchantSettlement settlement = settlement(status);
            when(settlements.lock(settlement.getId())).thenReturn(Optional.of(settlement));
            assertProblem(409, () -> service.recordPayment(settlement.getId(), new RecordPayment("bank-1", paidAt, true), KEY, dispatcher));
        }
        verify(ledger, never()).save(any());
    }

    @Test
    void paidAtMustFallBetweenApprovalAndNow() {
        MerchantSettlement settlement = settlement(SettlementStatus.APPROVED);
        when(settlements.findByPaidByAndPaymentIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(settlements.lock(settlement.getId())).thenReturn(Optional.of(settlement));

        OffsetDateTime beforeApproval = settlement.getApprovedAt().minusMinutes(1).atOffset(ZoneOffset.UTC);
        OffsetDateTime future = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10);
        assertProblem(400, () -> service.recordPayment(settlement.getId(), new RecordPayment("bank-1", beforeApproval, true), KEY, dispatcher));
        assertProblem(400, () -> service.recordPayment(settlement.getId(), new RecordPayment("bank-1", future, true), KEY, dispatcher));
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.APPROVED);
        verify(ledger, never()).save(any());
    }
}
