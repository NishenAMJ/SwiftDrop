package com.swiftdrop.payment.reconciliation.service;

import com.swiftdrop.payment.cod.entity.*;
import com.swiftdrop.payment.cod.repository.CodCollectionRepository;
import com.swiftdrop.payment.ledger.entity.*;
import com.swiftdrop.payment.ledger.repository.MerchantLedgerEntryRepository;
import com.swiftdrop.payment.onlinepayment.service.*;
import com.swiftdrop.payment.reconciliation.dto.CashHandoverDtos;
import com.swiftdrop.payment.reconciliation.entity.*;
import com.swiftdrop.payment.reconciliation.repository.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CashHandoverServiceTest {
    private final CashHandoverRepository handovers = mock(CashHandoverRepository.class);
    private final CashHandoverItemRepository items = mock(CashHandoverItemRepository.class);
    private final CashReconciliationRepository reconciliations = mock(CashReconciliationRepository.class);
    private final CodCollectionRepository collections = mock(CodCollectionRepository.class);
    private final MerchantLedgerEntryRepository ledger = mock(MerchantLedgerEntryRepository.class);
    private final CashHandoverService service = new CashHandoverService(
        handovers, items, reconciliations, collections, ledger);
    private final UUID courierId = UUID.fromString("aa6c790e-497a-4340-8039-79e25c95f40e");
    private final Actor courier = new Actor(courierId, null, Set.of("COURIER"), "test");
    private final Actor dispatcher = new Actor(UUID.fromString("bb6c790e-497a-4340-8039-79e25c95f40e"),
        null, Set.of("DISPATCHER"), "test");

    @Test
    void courierSubmitsOnlyOwnedUnassignedCollections() {
        CodCollection collection = collection(CodCollectionStatus.RECORDED);
        when(handovers.findByCourierIdAndIdempotencyKey(courierId, "handover-key-0001"))
            .thenReturn(Optional.empty());
        when(collections.lockAllById(anyCollection())).thenReturn(List.of(collection));

        CashHandoverDtos.Handover result = service.create(new CashHandoverDtos.Create(
            List.of(collection.getId()), new BigDecimal("1000.00"), "LKR"),
            "handover-key-0001", courier);

        assertEquals(new BigDecimal("1000.00"), result.expectedAmount());
        assertEquals("SUBMITTED", result.status());
        assertEquals(CodCollectionStatus.IN_HANDOVER, collection.getStatus());
        verify(handovers).saveAndFlush(any(CashHandover.class));
        verify(items).saveAll(anyList());
    }

    @Test
    void courierCannotReadAnotherCouriersHandover() {
        CashHandover handover = handover();
        handover.setCourierId(UUID.randomUUID());
        when(handovers.findById(handover.getId())).thenReturn(Optional.of(handover));

        PaymentProblem problem = assertThrows(PaymentProblem.class,
            () -> service.get(handover.getId(), courier));
        assertEquals(403, problem.status);
        verifyNoInteractions(items, reconciliations);
    }

    @Test
    void exactDispatcherConfirmationMakesCodLedgerEntryEligible() {
        CashHandover handover = handover();
        CodCollection collection = collection(CodCollectionStatus.IN_HANDOVER);
        CashHandoverItem item = new CashHandoverItem();
        item.setHandoverId(handover.getId());
        item.setCodCollectionId(collection.getId());
        MerchantLedgerEntry entry = new MerchantLedgerEntry();
        entry.setId(UUID.randomUUID());
        entry.setSettlementEligible(false);
        when(reconciliations.findByConfirmedByAndConfirmationIdempotencyKey(
            dispatcher.id(), "confirm-key-0001")).thenReturn(Optional.empty());
        when(handovers.lock(handover.getId())).thenReturn(Optional.of(handover));
        when(items.findByHandoverId(handover.getId())).thenReturn(List.of(item));
        when(collections.lockAllById(anyCollection())).thenReturn(List.of(collection));
        when(ledger.findByEntryTypeAndSourceReference(LedgerEntryType.COD_COLLECTION,
            "COD_COLLECTION:" + collection.getId())).thenReturn(Optional.of(entry));

        CashHandoverDtos.Handover result = service.confirm(handover.getId(),
            new CashHandoverDtos.Confirm(new BigDecimal("1000.00"), null),
            "confirm-key-0001", dispatcher);

        assertEquals("CONFIRMED", result.status());
        assertEquals("EXACT", result.reconciliation().status());
        assertEquals(CodCollectionStatus.RECONCILED, collection.getStatus());
        assertTrue(entry.isSettlementEligible());
        verify(reconciliations).save(any(CashReconciliation.class));
    }

    @Test
    void shortageRequiresInvestigationNote() {
        CashHandover handover = handover();
        when(reconciliations.findByConfirmedByAndConfirmationIdempotencyKey(
            dispatcher.id(), "confirm-key-0002")).thenReturn(Optional.empty());
        when(handovers.lock(handover.getId())).thenReturn(Optional.of(handover));

        PaymentProblem problem = assertThrows(PaymentProblem.class, () -> service.confirm(handover.getId(),
            new CashHandoverDtos.Confirm(new BigDecimal("900.00"), null),
            "confirm-key-0002", dispatcher));
        assertEquals(400, problem.status);
        verifyNoInteractions(ledger);
    }

    private CodCollection collection(CodCollectionStatus status) {
        CodCollection collection = new CodCollection();
        collection.setId(UUID.randomUUID());
        collection.setCourierId(courierId);
        collection.setCollectedAmount(new BigDecimal("1000.00"));
        collection.setCurrency("LKR");
        collection.setStatus(status);
        return collection;
    }

    private CashHandover handover() {
        CashHandover handover = new CashHandover();
        handover.setId(UUID.randomUUID());
        handover.setCourierId(courierId);
        handover.setExpectedAmount(new BigDecimal("1000.00"));
        handover.setSubmittedAmount(new BigDecimal("1000.00"));
        handover.setCurrency("LKR");
        handover.setStatus(CashHandoverStatus.SUBMITTED);
        handover.setSubmittedAt(LocalDateTime.now());
        return handover;
    }
}
