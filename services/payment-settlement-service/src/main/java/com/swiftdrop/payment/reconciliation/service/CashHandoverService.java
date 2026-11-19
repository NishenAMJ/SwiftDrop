package com.swiftdrop.payment.reconciliation.service;

import com.swiftdrop.payment.cod.entity.*;
import com.swiftdrop.payment.cod.repository.CodCollectionRepository;
import com.swiftdrop.payment.ledger.entity.LedgerEntryType;
import com.swiftdrop.payment.ledger.repository.MerchantLedgerEntryRepository;
import com.swiftdrop.payment.onlinepayment.service.*;
import com.swiftdrop.payment.reconciliation.dto.CashHandoverDtos;
import com.swiftdrop.payment.reconciliation.entity.*;
import com.swiftdrop.payment.reconciliation.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CashHandoverService {
    private final CashHandoverRepository handovers;
    private final CashHandoverItemRepository items;
    private final CashReconciliationRepository reconciliations;
    private final CodCollectionRepository collections;
    private final MerchantLedgerEntryRepository ledger;

    public CashHandoverService(CashHandoverRepository handovers,
            CashHandoverItemRepository items, CashReconciliationRepository reconciliations,
            CodCollectionRepository collections, MerchantLedgerEntryRepository ledger) {
        this.handovers = handovers;
        this.items = items;
        this.reconciliations = reconciliations;
        this.collections = collections;
        this.ledger = ledger;
    }

    @Transactional
    public CashHandoverDtos.Handover create(CashHandoverDtos.Create request, String key, Actor actor) {
        actor.requireCourier();
        validateKey(key);
        if (request == null || request.collectionIds() == null || request.collectionIds().isEmpty()
            || request.collectionIds().size() > 500 || request.collectionIds().stream().anyMatch(Objects::isNull)
            || !validMoney(request.submittedAmount(), false)
            || request.currency() == null || !request.currency().matches("^[A-Z]{3}$"))
            throw validation("Handover request is incomplete or invalid.");
        LinkedHashSet<UUID> requestedIds = new LinkedHashSet<>(request.collectionIds());
        if (requestedIds.size() != request.collectionIds().size())
            throw validation("collectionIds must not contain duplicates.");

        Optional<CashHandover> replay = handovers.findByCourierIdAndIdempotencyKey(actor.id(), key);
        if (replay.isPresent()) {
            List<UUID> existingIds = collectionIds(replay.get().getId());
            if (!moneyEquals(replay.get().getSubmittedAmount(), request.submittedAmount())
                || !replay.get().getCurrency().equals(request.currency())
                || !new HashSet<>(existingIds).equals(requestedIds))
                throw PaymentProblem.conflict("Idempotency-Key was already used with another handover request.");
            return view(replay.get());
        }

        List<CodCollection> selected = collections.lockAllById(requestedIds);
        if (selected.size() != requestedIds.size()) throw notFound("One or more COD collections were not found.");
        BigDecimal expected = BigDecimal.ZERO;
        for (CodCollection collection : selected) {
            if (!actor.id().equals(collection.getCourierId())) throw PaymentProblem.forbidden();
            if (collection.getStatus() != CodCollectionStatus.RECORDED
                || items.existsByCodCollectionId(collection.getId()))
                throw PaymentProblem.conflict("A COD collection is already assigned or ineligible.");
            if (!request.currency().equals(collection.getCurrency()))
                throw validation("All COD collections must use the handover currency.");
            expected = expected.add(collection.getCollectedAmount());
        }
        if (!validMoney(expected, false))
            throw validation("Handover total exceeds the supported amount.");

        CashHandover handover = new CashHandover();
        handover.setId(UUID.randomUUID());
        handover.setCourierId(actor.id());
        handover.setExpectedAmount(expected);
        handover.setSubmittedAmount(request.submittedAmount());
        handover.setCurrency(request.currency());
        handover.setStatus(CashHandoverStatus.SUBMITTED);
        handover.setIdempotencyKey(key);
        handover.setSubmittedAt(LocalDateTime.now(ZoneOffset.UTC));
        handovers.saveAndFlush(handover);

        List<CashHandoverItem> links = new ArrayList<>();
        for (CodCollection collection : selected) {
            CashHandoverItem link = new CashHandoverItem();
            link.setHandoverId(handover.getId());
            link.setCodCollectionId(collection.getId());
            links.add(link);
            collection.setStatus(CodCollectionStatus.IN_HANDOVER);
        }
        items.saveAll(links);
        collections.saveAll(selected);
        return view(handover, new ArrayList<>(requestedIds), null);
    }

    @Transactional(readOnly = true)
    public CashHandoverDtos.Handover get(UUID id, Actor actor) {
        CashHandover handover = handovers.findById(id).orElseThrow(() -> notFound("Handover was not found."));
        requireVisible(handover, actor);
        return view(handover);
    }

    @Transactional(readOnly = true)
    public CashHandoverDtos.Page list(Integer page, Integer size, String status, UUID courierId, Actor actor) {
        if (!actor.admin() && !actor.courier()) throw PaymentProblem.forbidden();
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? 20 : size;
        if (pageNumber < 0 || pageSize < 1 || pageSize > 100) throw validation("Invalid pagination.");
        CashHandoverStatus parsedStatus = parseStatus(status);
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "submittedAt"));
        org.springframework.data.domain.Page<CashHandover> result;
        if (actor.admin()) {
            if (courierId != null && parsedStatus != null)
                result = handovers.findByCourierIdAndStatus(courierId, parsedStatus, pageable);
            else if (courierId != null) result = handovers.findByCourierId(courierId, pageable);
            else if (parsedStatus != null) result = handovers.findByStatus(parsedStatus, pageable);
            else result = handovers.findAll(pageable);
        } else {
            if (courierId != null && !actor.id().equals(courierId)) throw PaymentProblem.forbidden();
            result = parsedStatus == null
                ? handovers.findByCourierId(actor.id(), pageable)
                : handovers.findByCourierIdAndStatus(actor.id(), parsedStatus, pageable);
        }
        List<CashHandoverDtos.Handover> content = result.getContent().stream().map(this::view).toList();
        return new CashHandoverDtos.Page(content, result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public CashHandoverDtos.Handover confirm(UUID id, CashHandoverDtos.Confirm request,
            String key, Actor actor) {
        actor.requireAdmin();
        validateKey(key);
        if (id == null || request == null || !validMoney(request.receivedAmount(), true)
            || (request.investigationNote() != null && request.investigationNote().length() > 500))
            throw validation("Confirmation request is incomplete or invalid.");
        Optional<CashReconciliation> replay = reconciliations
            .findByConfirmedByAndConfirmationIdempotencyKey(actor.id(), key);
        if (replay.isPresent()) {
            CashReconciliation existing = replay.get();
            if (!existing.getHandoverId().equals(id)
                || !moneyEquals(existing.getReceivedAmount(), request.receivedAmount())
                || !Objects.equals(existing.getInvestigationNote(), normalized(request.investigationNote())))
                throw PaymentProblem.conflict("Idempotency-Key was already used with another confirmation.");
            return view(handovers.findById(id).orElseThrow(() -> notFound("Handover was not found.")));
        }

        CashHandover handover = handovers.lock(id).orElseThrow(() -> notFound("Handover was not found."));
        if (handover.getStatus() != CashHandoverStatus.SUBMITTED)
            throw PaymentProblem.conflict("Handover is already finalized.");
        BigDecimal difference = request.receivedAmount().subtract(handover.getExpectedAmount());
        ReconciliationStatus status = difference.signum() == 0 ? ReconciliationStatus.EXACT
            : difference.signum() < 0 ? ReconciliationStatus.SHORT : ReconciliationStatus.EXCESS;
        String note = normalized(request.investigationNote());
        if (status != ReconciliationStatus.EXACT && note == null)
            throw validation("investigationNote is required for short or excess handovers.");

        CashReconciliation reconciliation = new CashReconciliation();
        reconciliation.setId(UUID.randomUUID());
        reconciliation.setHandoverId(id);
        reconciliation.setExpectedAmount(handover.getExpectedAmount());
        reconciliation.setReceivedAmount(request.receivedAmount());
        reconciliation.setDifferenceAmount(difference);
        reconciliation.setCurrency(handover.getCurrency());
        reconciliation.setStatus(status);
        reconciliation.setInvestigationNote(note);
        reconciliation.setConfirmedBy(actor.id());
        reconciliation.setConfirmationIdempotencyKey(key);
        reconciliation.setConfirmedAt(LocalDateTime.now(ZoneOffset.UTC));
        reconciliations.save(reconciliation);

        if (status == ReconciliationStatus.EXACT) {
            handover.setStatus(CashHandoverStatus.CONFIRMED);
            markCollectionsSettlementEligible(id);
        } else {
            handover.setStatus(CashHandoverStatus.FLAGGED);
        }
        handovers.save(handover);
        return view(handover, collectionIds(id), reconciliation);
    }

    private void markCollectionsSettlementEligible(UUID handoverId) {
        List<UUID> ids = collectionIds(handoverId);
        List<CodCollection> selected = collections.lockAllById(ids);
        if (selected.size() != ids.size()) throw PaymentProblem.conflict("Handover collections are inconsistent.");
        for (CodCollection collection : selected) {
            if (collection.getStatus() != CodCollectionStatus.IN_HANDOVER)
                throw PaymentProblem.conflict("Handover collection is not reconcilable.");
            collection.setStatus(CodCollectionStatus.RECONCILED);
            var entry = ledger.findByEntryTypeAndSourceReference(LedgerEntryType.COD_COLLECTION,
                "COD_COLLECTION:" + collection.getId())
                .orElseThrow(() -> PaymentProblem.conflict("COD ledger entry is missing."));
            entry.setSettlementEligible(true);
            ledger.save(entry);
        }
        collections.saveAll(selected);
    }

    private CashHandoverDtos.Handover view(CashHandover handover) {
        CashReconciliation reconciliation = reconciliations.findByHandoverId(handover.getId()).orElse(null);
        return view(handover, collectionIds(handover.getId()), reconciliation);
    }

    private CashHandoverDtos.Handover view(CashHandover handover, List<UUID> collectionIds,
            CashReconciliation reconciliation) {
        CashHandoverDtos.Reconciliation response = reconciliation == null ? null
            : new CashHandoverDtos.Reconciliation(reconciliation.getId(), reconciliation.getExpectedAmount(),
                reconciliation.getReceivedAmount(), reconciliation.getDifferenceAmount(),
                reconciliation.getCurrency(), reconciliation.getStatus().name(),
                reconciliation.getInvestigationNote(), reconciliation.getConfirmedBy(),
                reconciliation.getConfirmedAt());
        return new CashHandoverDtos.Handover(handover.getId(), handover.getCourierId(),
            List.copyOf(collectionIds), handover.getExpectedAmount(), handover.getSubmittedAmount(),
            handover.getCurrency(), handover.getStatus().name(), handover.getSubmittedAt(), response);
    }

    private List<UUID> collectionIds(UUID handoverId) {
        return items.findByHandoverId(handoverId).stream()
            .map(CashHandoverItem::getCodCollectionId).sorted().toList();
    }

    private static void requireVisible(CashHandover handover, Actor actor) {
        if (!actor.admin() && (!actor.courier() || !actor.id().equals(handover.getCourierId())))
            throw PaymentProblem.forbidden();
    }

    private static CashHandoverStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try { return CashHandoverStatus.valueOf(status); }
        catch (IllegalArgumentException invalid) { throw validation("Unknown handover status."); }
    }

    private static void validateKey(String key) {
        if (key == null || !key.matches("^[A-Za-z0-9._:-]{16,128}$"))
            throw validation("Idempotency-Key must contain 16 to 128 permitted characters.");
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean moneyEquals(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private static boolean validMoney(BigDecimal value, boolean zeroAllowed) {
        return value != null && (zeroAllowed ? value.signum() >= 0 : value.signum() > 0)
            && value.scale() <= 2 && value.precision() - value.scale() <= 10;
    }

    private static PaymentProblem validation(String message) {
        return new PaymentProblem(400, "VALIDATION_FAILED", message);
    }

    private static PaymentProblem notFound(String message) {
        return new PaymentProblem(404, "NOT_FOUND", message);
    }
}
