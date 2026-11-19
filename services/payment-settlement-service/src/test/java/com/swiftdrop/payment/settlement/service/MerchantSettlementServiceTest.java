package com.swiftdrop.payment.settlement.service;

import com.swiftdrop.payment.ledger.entity.*;
import com.swiftdrop.payment.ledger.repository.MerchantLedgerEntryRepository;
import com.swiftdrop.payment.onlinepayment.service.Actor;
import com.swiftdrop.payment.settlement.dto.MerchantSettlementDtos;
import com.swiftdrop.payment.settlement.entity.*;
import com.swiftdrop.payment.settlement.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MerchantSettlementServiceTest {
    private final MerchantSettlementRepository settlements = mock(MerchantSettlementRepository.class);
    private final MerchantSettlementEntryRepository settlementEntries = mock(MerchantSettlementEntryRepository.class);
    private final MerchantLedgerEntryRepository ledger = mock(MerchantLedgerEntryRepository.class);
    private final MerchantSettlementService service = new MerchantSettlementService(settlements, settlementEntries, ledger);
    private final UUID merchantId = UUID.fromString("846c790e-497a-4340-8039-79e25c95f40e");
    private final Actor dispatcher = new Actor(UUID.randomUUID(), null, Set.of("DISPATCHER"), "test");

    @Test
    void calculationFreezesEligibleEntriesAndUsesSignedTotal() {
        MerchantLedgerEntry credit = entry(new BigDecimal("1000.00"));
        MerchantLedgerEntry charge = entry(new BigDecimal("-100.00"));
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1).withNano(0);
        when(settlements.findByCalculatedByAndIdempotencyKey(dispatcher.id(), "settlement-key-01"))
            .thenReturn(Optional.empty());
        when(ledger.findUnsettledThrough(merchantId, "LKR", cutoff)).thenReturn(List.of(credit, charge));

        MerchantSettlementDtos.View result = service.calculate(
            new MerchantSettlementDtos.Calculate(merchantId, "LKR", cutoff),
            "settlement-key-01", dispatcher);

        assertEquals(new BigDecimal("900.00"), result.amount());
        assertEquals("CALCULATED", result.status());
        assertEquals(Set.of(credit.getId(), charge.getId()), new HashSet<>(result.ledgerEntryIds()));
        verify(settlements).saveAndFlush(any(MerchantSettlement.class));
        verify(settlementEntries).saveAll(anyList());
    }

    @Test
    void externalPaymentCreatesOneBalancingLedgerEntry() {
        MerchantSettlement settlement = new MerchantSettlement();
        settlement.setId(UUID.randomUUID());
        settlement.setMerchantId(merchantId);
        settlement.setAmount(new BigDecimal("900.00"));
        settlement.setCurrency("LKR");
        settlement.setStatus(SettlementStatus.APPROVED);
        settlement.setApprovedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(2));
        when(settlements.findByPaidByAndPaymentIdempotencyKey(dispatcher.id(), "payment-key-0001"))
            .thenReturn(Optional.empty());
        when(settlements.lock(settlement.getId())).thenReturn(Optional.of(settlement));
        when(settlements.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledger.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(settlementEntries.findBySettlementId(settlement.getId())).thenReturn(List.of());
        LocalDateTime paidAt = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1).withNano(0);

        MerchantSettlementDtos.View result = service.recordPayment(settlement.getId(),
            new MerchantSettlementDtos.RecordPayment("bank-transfer-123", paidAt, true),
            "payment-key-0001", dispatcher);

        assertEquals("PAID", result.status());
        var captured = org.mockito.ArgumentCaptor.forClass(MerchantLedgerEntry.class);
        verify(ledger).save(captured.capture());
        assertEquals(new BigDecimal("-900.00"), captured.getValue().getAmount());
        assertEquals(LedgerEntryType.SETTLEMENT, captured.getValue().getEntryType());
        assertFalse(captured.getValue().isSettlementEligible());
    }

    private MerchantLedgerEntry entry(BigDecimal amount) {
        MerchantLedgerEntry entry = new MerchantLedgerEntry();
        entry.setId(UUID.randomUUID());
        entry.setMerchantId(merchantId);
        entry.setAmount(amount);
        entry.setCurrency("LKR");
        entry.setSettlementEligible(true);
        return entry;
    }
}
