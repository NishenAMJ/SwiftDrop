package com.swiftdrop.payment.cod.service;

import com.swiftdrop.payment.cod.entity.*;
import com.swiftdrop.payment.cod.repository.CodCollectionRepository;
import com.swiftdrop.payment.ledger.entity.*;
import com.swiftdrop.payment.ledger.repository.MerchantLedgerEntryRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CodCollectionServiceTest {
    private final CodCollectionRepository collections = mock(CodCollectionRepository.class);
    private final MerchantLedgerEntryRepository ledger = mock(MerchantLedgerEntryRepository.class);
    private final CodAmountVerifier verifier = mock(CodAmountVerifier.class);
    private final CodCollectionService service = new CodCollectionService(collections, ledger, verifier);

    @Test
    void validEventCreatesCollectionAndLedgerEntryOnce() {
        CodCollectedEvent event = event(UUID.randomUUID());
        when(collections.findBySourceEventId(event.eventId())).thenReturn(Optional.empty());
        when(collections.findByBookingId(event.data().bookingId())).thenReturn(Optional.empty());

        CodCollection result = service.record(event);

        verify(verifier).verify(event.data());
        verify(collections).save(result);
        ArgumentCaptor<MerchantLedgerEntry> entry = ArgumentCaptor.forClass(MerchantLedgerEntry.class);
        verify(ledger).save(entry.capture());
        assertEquals(event.eventId(), result.getSourceEventId());
        assertEquals(CodCollectionStatus.RECORDED, result.getStatus());
        assertEquals(0, event.data().collectedAmount().compareTo(entry.getValue().getAmount()));
        assertEquals(LedgerEntryType.COD_COLLECTION, entry.getValue().getEntryType());
        assertEquals("COD_EVENT:" + event.eventId(), entry.getValue().getIdempotencyKey());
        assertFalse(entry.getValue().isSettlementEligible());
    }

    @Test
    void duplicateEventReturnsExistingRecordWithoutSecondLedgerEntry() {
        CodCollectedEvent event = event(UUID.randomUUID());
        CodCollection existing = existing(event);
        when(collections.findBySourceEventId(event.eventId())).thenReturn(Optional.of(existing));

        assertSame(existing, service.record(event));

        verifyNoInteractions(verifier, ledger);
        verify(collections, never()).save(any());
    }

    @Test
    void duplicateEventWithDifferentAmountIsRejected() {
        CodCollectedEvent event = event(UUID.randomUUID());
        CodCollection existing = existing(event);
        existing.setCollectedAmount(new BigDecimal("900.00"));
        when(collections.findBySourceEventId(event.eventId())).thenReturn(Optional.of(existing));

        assertThrows(InvalidCodEvent.class, () -> service.record(event));
        verifyNoInteractions(verifier, ledger);
    }

    @Test
    void malformedEventIsRejectedBeforePersistence() {
        CodCollectedEvent valid = event(UUID.randomUUID());
        CodCollectedEvent invalid = new CodCollectedEvent(valid.eventId(), "delivery.delivered", 1,
            valid.occurredAt(), valid.producer(), valid.data());

        assertThrows(InvalidCodEvent.class, () -> service.record(invalid));
        verifyNoInteractions(collections, ledger, verifier);
    }

    private static CodCollectedEvent event(UUID eventId) {
        return new CodCollectedEvent(eventId, "delivery.cod-collected", 1,
            OffsetDateTime.now(ZoneOffset.UTC), "delivery-operations-service",
            new CodCollectedEvent.Data("b59b1f85-f260-4620-a6a8-f5ff726f5382", "SWD-2026-000125",
                "SNP-1234ABCD", UUID.fromString("846c790e-497a-4340-8039-79e25c95f40e"),
                UUID.fromString("aa6c790e-497a-4340-8039-79e25c95f40e"), UUID.randomUUID(), "COD",
                new BigDecimal("1000.00"), new BigDecimal("1000.00"), "LKR",
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1)));
    }

    private static CodCollection existing(CodCollectedEvent event) {
        CodCollection collection = new CodCollection();
        collection.setId(UUID.randomUUID());
        collection.setBookingId(event.data().bookingId());
        collection.setPricingSnapshotId(event.data().pricingSnapshotId());
        collection.setMerchantId(event.data().merchantId());
        collection.setCourierId(event.data().courierId());
        collection.setDeliveryAttemptId(event.data().deliveryAttemptId());
        collection.setExpectedAmount(event.data().expectedAmount());
        collection.setCollectedAmount(event.data().collectedAmount());
        collection.setCurrency(event.data().currency());
        collection.setSourceEventId(event.eventId());
        return collection;
    }
}
