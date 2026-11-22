package com.swiftdrop.payment.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.payment.cod.entity.CodCollection;
import com.swiftdrop.payment.cod.entity.CodCollectionStatus;
import com.swiftdrop.payment.cod.repository.CodCollectionRepository;
import com.swiftdrop.payment.ledger.entity.LedgerEntryType;
import com.swiftdrop.payment.ledger.repository.MerchantLedgerEntryRepository;
import com.swiftdrop.payment.onlinepayment.service.Actor;
import com.swiftdrop.payment.onlinepayment.service.PaymentProblem;
import com.swiftdrop.payment.reconciliation.dto.CashHandoverDtos;
import com.swiftdrop.payment.reconciliation.entity.CashHandover;
import com.swiftdrop.payment.reconciliation.entity.CashHandoverItem;
import com.swiftdrop.payment.reconciliation.entity.CashHandoverStatus;
import com.swiftdrop.payment.reconciliation.entity.CashReconciliation;
import com.swiftdrop.payment.reconciliation.entity.ReconciliationStatus;
import com.swiftdrop.payment.reconciliation.repository.CashHandoverItemRepository;
import com.swiftdrop.payment.reconciliation.repository.CashHandoverRepository;
import com.swiftdrop.payment.reconciliation.repository.CashReconciliationRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Validation, idempotency, authorization and reconciliation edge cases for courier cash handovers. */
class CashHandoverServiceEdgeCaseTest {
    private final CashHandoverRepository handovers = mock(CashHandoverRepository.class);
    private final CashHandoverItemRepository items = mock(CashHandoverItemRepository.class);
    private final CashReconciliationRepository reconciliations = mock(CashReconciliationRepository.class);
    private final CodCollectionRepository collections = mock(CodCollectionRepository.class);
    private final MerchantLedgerEntryRepository ledger = mock(MerchantLedgerEntryRepository.class);
    private final CashHandoverService service = new CashHandoverService(handovers, items, reconciliations, collections, ledger);

    private final UUID courierId = UUID.fromString("aa6c790e-497a-4340-8039-79e25c95f40e");
    private final Actor courier = new Actor(courierId, null, Set.of("COURIER"), "test");
    private final Actor dispatcher = new Actor(UUID.randomUUID(), null, Set.of("DISPATCHER"), "test");
    private final Actor customer = new Actor(UUID.randomUUID(), null, Set.of("CUSTOMER"), "test");
    private static final String KEY = "handover-key-0001";

    private static void assertProblem(int status, Executable call) {
        assertThatThrownBy(call::execute).isInstanceOfSatisfying(PaymentProblem.class,
                problem -> assertThat(problem.status).isEqualTo(status));
    }

    private CashHandoverDtos.Create request(String amount, String currency, UUID... ids) {
        return new CashHandoverDtos.Create(Arrays.asList(ids), amount == null ? null : new BigDecimal(amount), currency);
    }

    // ---------- create ----------

    @Test
    void onlyCouriersCanSubmitHandovers() {
        assertProblem(403, () -> service.create(request("10", "LKR", UUID.randomUUID()), KEY, dispatcher));
        verifyNoInteractions(handovers, collections);
    }

    @Test
    void createRejectsInvalidRequestsBeforeTouchingTheDatabase() {
        UUID id = UUID.randomUUID();
        assertProblem(400, () -> service.create(null, KEY, courier));
        assertProblem(400, () -> service.create(new CashHandoverDtos.Create(null, BigDecimal.TEN, "LKR"), KEY, courier));
        assertProblem(400, () -> service.create(request("10", "LKR"), KEY, courier));
        assertProblem(400, () -> service.create(request("10", "LKR", id, null), KEY, courier));
        assertProblem(400, () -> service.create(request("0", "LKR", id), KEY, courier));
        assertProblem(400, () -> service.create(request("-1", "LKR", id), KEY, courier));
        assertProblem(400, () -> service.create(request("10.001", "LKR", id), KEY, courier));
        assertProblem(400, () -> service.create(request("12345678901", "LKR", id), KEY, courier));
        assertProblem(400, () -> service.create(request(null, "LKR", id), KEY, courier));
        assertProblem(400, () -> service.create(request("10", "lkr", id), KEY, courier));
        assertProblem(400, () -> service.create(request("10", null, id), KEY, courier));
        assertProblem(400, () -> service.create(request("10", "LKR", id, id), KEY, courier));
        assertProblem(400, () -> service.create(request("10", "LKR", id), "short", courier));
        assertProblem(400, () -> service.create(request("10", "LKR", id), null, courier));
        verifyNoInteractions(handovers, collections);
    }

    @Test
    void createRejectsMoreThanFiveHundredCollections() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 501; i++) ids.add(UUID.randomUUID());
        assertProblem(400, () -> service.create(new CashHandoverDtos.Create(ids, BigDecimal.TEN, "LKR"), KEY, courier));
    }

    @Test
    void replayWithTheSameRequestReturnsTheOriginalHandover() {
        CodCollection collection = collection(CodCollectionStatus.IN_HANDOVER, "1000.00", "LKR");
        CashHandover existing = handover(CashHandoverStatus.SUBMITTED);
        when(handovers.findByCourierIdAndIdempotencyKey(courierId, KEY)).thenReturn(Optional.of(existing));
        when(items.findByHandoverId(existing.getId())).thenReturn(List.of(item(existing, collection)));
        when(reconciliations.findByHandoverId(existing.getId())).thenReturn(Optional.empty());

        var result = service.create(request("1000.0", "LKR", collection.getId()), KEY, courier);

        assertThat(result.id()).isEqualTo(existing.getId());
        verify(collections, never()).lockAllById(anyCollection());
        verify(handovers, never()).saveAndFlush(any());
    }

    @Test
    void replayWithDifferentDetailsIsAConflict() {
        CodCollection collection = collection(CodCollectionStatus.IN_HANDOVER, "1000.00", "LKR");
        CashHandover existing = handover(CashHandoverStatus.SUBMITTED);
        when(handovers.findByCourierIdAndIdempotencyKey(courierId, KEY)).thenReturn(Optional.of(existing));
        when(items.findByHandoverId(existing.getId())).thenReturn(List.of(item(existing, collection)));

        assertProblem(409, () -> service.create(request("999.00", "LKR", collection.getId()), KEY, courier));
        assertProblem(409, () -> service.create(request("1000.00", "USD", collection.getId()), KEY, courier));
        assertProblem(409, () -> service.create(request("1000.00", "LKR", UUID.randomUUID()), KEY, courier));
    }

    @Test
    void createFailsWhenACollectionDoesNotExist() {
        when(handovers.findByCourierIdAndIdempotencyKey(courierId, KEY)).thenReturn(Optional.empty());
        when(collections.lockAllById(anyCollection())).thenReturn(List.of());
        assertProblem(404, () -> service.create(request("10", "LKR", UUID.randomUUID()), KEY, courier));
    }

    @Test
    void courierCannotHandOverAnotherCouriersCash() {
        CodCollection collection = collection(CodCollectionStatus.RECORDED, "1000.00", "LKR");
        collection.setCourierId(UUID.randomUUID());
        when(handovers.findByCourierIdAndIdempotencyKey(courierId, KEY)).thenReturn(Optional.empty());
        when(collections.lockAllById(anyCollection())).thenReturn(List.of(collection));
        assertProblem(403, () -> service.create(request("1000", "LKR", collection.getId()), KEY, courier));
        verify(handovers, never()).saveAndFlush(any());
    }

    @Test
    void collectionAlreadyInAHandoverCannotBeSubmittedAgain() {
        CodCollection inHandover = collection(CodCollectionStatus.IN_HANDOVER, "1000.00", "LKR");
        when(handovers.findByCourierIdAndIdempotencyKey(courierId, KEY)).thenReturn(Optional.empty());
        when(collections.lockAllById(anyCollection())).thenReturn(List.of(inHandover));
        assertProblem(409, () -> service.create(request("1000", "LKR", inHandover.getId()), KEY, courier));

        CodCollection linked = collection(CodCollectionStatus.RECORDED, "1000.00", "LKR");
        when(collections.lockAllById(anyCollection())).thenReturn(List.of(linked));
        when(items.existsByCodCollectionId(linked.getId())).thenReturn(true);
        assertProblem(409, () -> service.create(request("1000", "LKR", linked.getId()), KEY, courier));
    }

    @Test
    void collectionsMustUseTheHandoverCurrency() {
        CodCollection usd = collection(CodCollectionStatus.RECORDED, "10.00", "USD");
        when(handovers.findByCourierIdAndIdempotencyKey(courierId, KEY)).thenReturn(Optional.empty());
        when(collections.lockAllById(anyCollection())).thenReturn(List.of(usd));
        assertProblem(400, () -> service.create(request("10", "LKR", usd.getId()), KEY, courier));
    }

    @Test
    void handoverTotalMustStayWithinTheSupportedAmount() {
        CodCollection a = collection(CodCollectionStatus.RECORDED, "9999999999.99", "LKR");
        CodCollection b = collection(CodCollectionStatus.RECORDED, "1.00", "LKR");
        when(handovers.findByCourierIdAndIdempotencyKey(courierId, KEY)).thenReturn(Optional.empty());
        when(collections.lockAllById(anyCollection())).thenReturn(List.of(a, b));
        assertProblem(400, () -> service.create(request("10", "LKR", a.getId(), b.getId()), KEY, courier));
    }

    @Test
    void submittedAmountMayDifferFromExpectedAndIsReconciledLater() {
        CodCollection a = collection(CodCollectionStatus.RECORDED, "600.00", "LKR");
        CodCollection b = collection(CodCollectionStatus.RECORDED, "400.00", "LKR");
        when(handovers.findByCourierIdAndIdempotencyKey(courierId, KEY)).thenReturn(Optional.empty());
        when(collections.lockAllById(anyCollection())).thenReturn(List.of(a, b));

        var result = service.create(request("950.00", "LKR", a.getId(), b.getId()), KEY, courier);

        assertThat(result.expectedAmount()).isEqualByComparingTo("1000.00");
        assertThat(result.submittedAmount()).isEqualByComparingTo("950.00");
        assertThat(a.getStatus()).isEqualTo(CodCollectionStatus.IN_HANDOVER);
        assertThat(b.getStatus()).isEqualTo(CodCollectionStatus.IN_HANDOVER);
    }

    // ---------- get / list ----------

    @Test
    void getIsVisibleToTheOwningCourierAndDispatchersOnly() {
        CashHandover handover = handover(CashHandoverStatus.SUBMITTED);
        when(handovers.findById(handover.getId())).thenReturn(Optional.of(handover));
        when(items.findByHandoverId(handover.getId())).thenReturn(List.of());
        when(reconciliations.findByHandoverId(handover.getId())).thenReturn(Optional.empty());

        assertThat(service.get(handover.getId(), courier).id()).isEqualTo(handover.getId());
        assertThat(service.get(handover.getId(), dispatcher).status()).isEqualTo("SUBMITTED");
        assertProblem(403, () -> service.get(handover.getId(), customer));
        assertProblem(404, () -> service.get(UUID.randomUUID(), dispatcher));
    }

    @Test
    void listRejectsUnauthorisedRolesInvalidPagesAndUnknownStatuses() {
        assertProblem(403, () -> service.list(0, 20, null, null, customer));
        assertProblem(400, () -> service.list(-1, 20, null, null, dispatcher));
        assertProblem(400, () -> service.list(0, 0, null, null, dispatcher));
        assertProblem(400, () -> service.list(0, 101, null, null, dispatcher));
        assertProblem(400, () -> service.list(0, 20, "LOST", null, dispatcher));
        assertProblem(403, () -> service.list(0, 20, null, UUID.randomUUID(), courier));
        verifyNoInteractions(handovers);
    }

    @Test
    void dispatcherListUsesTheMatchingFilter() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "submittedAt"));
        UUID other = UUID.randomUUID();
        when(handovers.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));
        when(handovers.findByCourierId(other, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));
        when(handovers.findByStatus(CashHandoverStatus.FLAGGED, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));
        when(handovers.findByCourierIdAndStatus(other, CashHandoverStatus.FLAGGED, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.list(null, null, " ", null, dispatcher);
        service.list(0, 20, null, other, dispatcher);
        service.list(0, 20, "FLAGGED", null, dispatcher);
        service.list(0, 20, "FLAGGED", other, dispatcher);

        verify(handovers).findAll(pageable);
        verify(handovers).findByCourierId(other, pageable);
        verify(handovers).findByStatus(CashHandoverStatus.FLAGGED, pageable);
        verify(handovers).findByCourierIdAndStatus(other, CashHandoverStatus.FLAGGED, pageable);
    }

    @Test
    void courierListIsAlwaysScopedToTheCourier() {
        Pageable pageable = PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "submittedAt"));
        CashHandover own = handover(CashHandoverStatus.SUBMITTED);
        when(handovers.findByCourierId(courierId, pageable)).thenReturn(new PageImpl<>(List.of(own), pageable, 6));
        when(handovers.findByCourierIdAndStatus(courierId, CashHandoverStatus.CONFIRMED, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));
        when(items.findByHandoverId(own.getId())).thenReturn(List.of());
        when(reconciliations.findByHandoverId(own.getId())).thenReturn(Optional.empty());

        var page = service.list(1, 5, null, courierId, courier);
        service.list(1, 5, "CONFIRMED", null, courier);

        assertThat(page.content()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(6);
        verify(handovers, never()).findAll(any(Pageable.class));
    }

    // ---------- confirm ----------

    @Test
    void onlyDispatchersConfirmAndRequestsAreValidated() {
        UUID id = UUID.randomUUID();
        assertProblem(403, () -> service.confirm(id, new CashHandoverDtos.Confirm(BigDecimal.TEN, null), KEY, courier));
        assertProblem(400, () -> service.confirm(null, new CashHandoverDtos.Confirm(BigDecimal.TEN, null), KEY, dispatcher));
        assertProblem(400, () -> service.confirm(id, null, KEY, dispatcher));
        assertProblem(400, () -> service.confirm(id, new CashHandoverDtos.Confirm(new BigDecimal("-1"), null), KEY, dispatcher));
        assertProblem(400, () -> service.confirm(id, new CashHandoverDtos.Confirm(BigDecimal.TEN, "n".repeat(501)), KEY, dispatcher));
        assertProblem(400, () -> service.confirm(id, new CashHandoverDtos.Confirm(BigDecimal.TEN, null), "bad key", dispatcher));
        verifyNoInteractions(handovers, reconciliations);
    }

    @Test
    void confirmationReplayReturnsTheSameResultAndRejectsChangedDetails() {
        CashHandover handover = handover(CashHandoverStatus.FLAGGED);
        CashReconciliation previous = new CashReconciliation();
        previous.setHandoverId(handover.getId());
        previous.setReceivedAmount(new BigDecimal("900.00"));
        previous.setInvestigationNote("Short by 100");
        when(reconciliations.findByConfirmedByAndConfirmationIdempotencyKey(dispatcher.id(), KEY))
                .thenReturn(Optional.of(previous));
        when(handovers.findById(handover.getId())).thenReturn(Optional.of(handover));
        when(items.findByHandoverId(handover.getId())).thenReturn(List.of());

        var replay = service.confirm(handover.getId(), new CashHandoverDtos.Confirm(new BigDecimal("900"), "  Short by 100  "),
                KEY, dispatcher);
        assertThat(replay.status()).isEqualTo("FLAGGED");
        assertProblem(409, () -> service.confirm(handover.getId(),
                new CashHandoverDtos.Confirm(new BigDecimal("901"), "Short by 100"), KEY, dispatcher));
        assertProblem(409, () -> service.confirm(handover.getId(),
                new CashHandoverDtos.Confirm(new BigDecimal("900"), "Different note"), KEY, dispatcher));
        assertProblem(409, () -> service.confirm(UUID.randomUUID(),
                new CashHandoverDtos.Confirm(new BigDecimal("900"), "Short by 100"), KEY, dispatcher));
        verify(handovers, never()).lock(any());
    }

    @Test
    void confirmingAMissingOrFinalisedHandoverFails() {
        when(reconciliations.findByConfirmedByAndConfirmationIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        UUID missing = UUID.randomUUID();
        when(handovers.lock(missing)).thenReturn(Optional.empty());
        assertProblem(404, () -> service.confirm(missing, new CashHandoverDtos.Confirm(BigDecimal.TEN, null), KEY, dispatcher));

        CashHandover confirmed = handover(CashHandoverStatus.CONFIRMED);
        when(handovers.lock(confirmed.getId())).thenReturn(Optional.of(confirmed));
        assertProblem(409, () -> service.confirm(confirmed.getId(),
                new CashHandoverDtos.Confirm(new BigDecimal("1000"), null), KEY, dispatcher));
        verify(reconciliations, never()).save(any());
    }

    @Test
    void excessCashIsFlaggedForInvestigationAndNotMadeSettlementEligible() {
        CashHandover handover = handover(CashHandoverStatus.SUBMITTED);
        when(reconciliations.findByConfirmedByAndConfirmationIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(handovers.lock(handover.getId())).thenReturn(Optional.of(handover));
        when(items.findByHandoverId(handover.getId())).thenReturn(List.of());

        assertProblem(400, () -> service.confirm(handover.getId(),
                new CashHandoverDtos.Confirm(new BigDecimal("1100"), "   "), KEY, dispatcher));

        var result = service.confirm(handover.getId(),
                new CashHandoverDtos.Confirm(new BigDecimal("1100.00"), "Courier returned extra change"), KEY, dispatcher);
        assertThat(result.status()).isEqualTo("FLAGGED");
        assertThat(result.reconciliation().status()).isEqualTo(ReconciliationStatus.EXCESS.name());
        assertThat(result.reconciliation().differenceAmount()).isEqualByComparingTo("100.00");
        verifyNoInteractions(ledger);
    }

    @Test
    void exactConfirmationFailsWhenCollectionsAreInconsistentOrLedgerEntryIsMissing() {
        CashHandover handover = handover(CashHandoverStatus.SUBMITTED);
        CodCollection collection = collection(CodCollectionStatus.IN_HANDOVER, "1000.00", "LKR");
        when(reconciliations.findByConfirmedByAndConfirmationIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(handovers.lock(handover.getId())).thenReturn(Optional.of(handover));
        when(items.findByHandoverId(handover.getId())).thenReturn(List.of(item(handover, collection)));
        var exact = new CashHandoverDtos.Confirm(new BigDecimal("1000.00"), null);

        when(collections.lockAllById(anyCollection())).thenReturn(List.of());
        assertProblem(409, () -> service.confirm(handover.getId(), exact, KEY, dispatcher));

        collection.setStatus(CodCollectionStatus.RECONCILED);
        when(collections.lockAllById(anyCollection())).thenReturn(List.of(collection));
        assertProblem(409, () -> service.confirm(handover.getId(), exact, KEY, dispatcher));

        collection.setStatus(CodCollectionStatus.IN_HANDOVER);
        when(ledger.findByEntryTypeAndSourceReference(LedgerEntryType.COD_COLLECTION, "COD_COLLECTION:" + collection.getId()))
                .thenReturn(Optional.empty());
        assertProblem(409, () -> service.confirm(handover.getId(), exact, KEY, dispatcher));
    }

    // ---------- fixtures ----------

    private CodCollection collection(CodCollectionStatus status, String amount, String currency) {
        CodCollection collection = new CodCollection();
        collection.setId(UUID.randomUUID());
        collection.setCourierId(courierId);
        collection.setCollectedAmount(new BigDecimal(amount));
        collection.setCurrency(currency);
        collection.setStatus(status);
        return collection;
    }

    private CashHandover handover(CashHandoverStatus status) {
        CashHandover handover = new CashHandover();
        handover.setId(UUID.randomUUID());
        handover.setCourierId(courierId);
        handover.setExpectedAmount(new BigDecimal("1000.00"));
        handover.setSubmittedAmount(new BigDecimal("1000.00"));
        handover.setCurrency("LKR");
        handover.setStatus(status);
        handover.setSubmittedAt(LocalDateTime.now());
        return handover;
    }

    private static CashHandoverItem item(CashHandover handover, CodCollection collection) {
        CashHandoverItem item = new CashHandoverItem();
        item.setHandoverId(handover.getId());
        item.setCodCollectionId(collection.getId());
        return item;
    }
}
