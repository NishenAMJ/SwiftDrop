package com.swiftdrop.payment.settlement.service;

import com.swiftdrop.payment.ledger.entity.*;
import com.swiftdrop.payment.ledger.repository.MerchantLedgerEntryRepository;
import com.swiftdrop.payment.onlinepayment.service.*;
import com.swiftdrop.payment.settlement.dto.MerchantSettlementDtos;
import com.swiftdrop.payment.settlement.entity.*;
import com.swiftdrop.payment.settlement.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantSettlementService {
    private final MerchantSettlementRepository settlements;
    private final MerchantSettlementEntryRepository settlementEntries;
    private final MerchantLedgerEntryRepository ledger;

    public MerchantSettlementService(MerchantSettlementRepository settlements,
            MerchantSettlementEntryRepository settlementEntries, MerchantLedgerEntryRepository ledger) {
        this.settlements = settlements;
        this.settlementEntries = settlementEntries;
        this.ledger = ledger;
    }

    @Transactional
    public MerchantSettlementDtos.View calculate(MerchantSettlementDtos.Calculate request,
            String key, Actor actor) {
        actor.requireAdmin();
        validateKey(key);
        validate(request);
        LocalDateTime cutoff = micros(request.cutoffAt());
        Optional<MerchantSettlement> replay = settlements.findByCalculatedByAndIdempotencyKey(actor.id(), key);
        if (replay.isPresent()) {
            MerchantSettlement existing = replay.get();
            if (!existing.getMerchantId().equals(request.merchantId())
                || !existing.getCurrency().equals(request.currency())
                || !existing.getCutoffAt().equals(cutoff))
                throw PaymentProblem.conflict("Idempotency-Key was already used for another settlement calculation.");
            return view(existing);
        }

        List<MerchantLedgerEntry> eligible = ledger.findUnsettledThrough(
            request.merchantId(), request.currency(), cutoff);
        BigDecimal total = eligible.stream().map(MerchantLedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (eligible.isEmpty() || total.signum() <= 0)
            throw PaymentProblem.conflict("There is no positive eligible balance to settle.");
        if (!validPositiveMoney(total))
            throw validation("Settlement total exceeds the supported amount.");

        MerchantSettlement settlement = new MerchantSettlement();
        settlement.setId(UUID.randomUUID());
        settlement.setMerchantId(request.merchantId());
        settlement.setAmount(total);
        settlement.setCurrency(request.currency());
        settlement.setCutoffAt(cutoff);
        settlement.setStatus(SettlementStatus.CALCULATED);
        settlement.setIdempotencyKey(key);
        settlement.setCalculatedBy(actor.id());
        settlement.setCalculatedAt(LocalDateTime.now(ZoneOffset.UTC));
        settlements.saveAndFlush(settlement);

        List<MerchantSettlementEntry> links = new ArrayList<>();
        for (MerchantLedgerEntry entry : eligible) {
            MerchantSettlementEntry link = new MerchantSettlementEntry();
            link.setSettlementId(settlement.getId());
            link.setLedgerEntryId(entry.getId());
            links.add(link);
        }
        settlementEntries.saveAll(links);
        return view(settlement, eligible.stream().map(MerchantLedgerEntry::getId).sorted().toList());
    }

    @Transactional(readOnly = true)
    public MerchantSettlementDtos.View get(UUID id, Actor actor) {
        MerchantSettlement settlement = settlements.findById(id)
            .orElseThrow(() -> notFound("Settlement was not found."));
        if (!actor.admin() && (!actor.merchant() || !actor.merchantId().equals(settlement.getMerchantId())))
            throw PaymentProblem.forbidden();
        return view(settlement);
    }

    @Transactional
    public MerchantSettlementDtos.View approve(UUID id, MerchantSettlementDtos.Approve request,
            String key, Actor actor) {
        actor.requireAdmin();
        validateKey(key);
        if (request == null || !request.confirmCalculatedAmount()
            || (request.note() != null && request.note().length() > 500))
            throw validation("Settlement approval is incomplete or invalid.");
        String note = normalized(request.note());
        Optional<MerchantSettlement> replay = settlements.findByApprovedByAndApprovalIdempotencyKey(actor.id(), key);
        if (replay.isPresent()) {
            MerchantSettlement existing = replay.get();
            if (!existing.getId().equals(id) || !Objects.equals(existing.getApprovalNote(), note))
                throw PaymentProblem.conflict("Idempotency-Key was already used for another approval.");
            return view(existing);
        }
        MerchantSettlement settlement = settlements.lock(id)
            .orElseThrow(() -> notFound("Settlement was not found."));
        if (settlement.getStatus() != SettlementStatus.CALCULATED)
            throw PaymentProblem.conflict("Only a calculated settlement can be approved.");
        settlement.setApprovedBy(actor.id());
        settlement.setApprovedAt(LocalDateTime.now(ZoneOffset.UTC));
        settlement.setApprovalNote(note);
        settlement.setApprovalIdempotencyKey(key);
        settlement.setStatus(SettlementStatus.APPROVED);
        return view(settlements.save(settlement));
    }

    @Transactional
    public MerchantSettlementDtos.View recordPayment(UUID id, MerchantSettlementDtos.RecordPayment request,
            String key, Actor actor) {
        actor.requireAdmin();
        validateKey(key);
        validate(request);
        String reference = request.externalReference().trim();
        LocalDateTime paidAt = micros(request.paidAt());
        Optional<MerchantSettlement> replay = settlements.findByPaidByAndPaymentIdempotencyKey(actor.id(), key);
        if (replay.isPresent()) {
            MerchantSettlement existing = replay.get();
            if (!existing.getId().equals(id) || !Objects.equals(existing.getExternalPaymentReference(), reference)
                || !Objects.equals(existing.getPaidAt(), paidAt))
                throw PaymentProblem.conflict("Idempotency-Key was already used for another settlement payment.");
            return view(existing);
        }
        MerchantSettlement settlement = settlements.lock(id)
            .orElseThrow(() -> notFound("Settlement was not found."));
        if (settlement.getStatus() != SettlementStatus.APPROVED)
            throw PaymentProblem.conflict("Only an approved settlement can be marked paid.");
        if (paidAt.isBefore(settlement.getApprovedAt())
            || paidAt.isAfter(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5)))
            throw validation("paidAt must be after approval and cannot be in the future.");

        settlement.setPaidBy(actor.id());
        settlement.setPaidAt(paidAt);
        settlement.setExternalPaymentReference(reference);
        settlement.setPaymentIdempotencyKey(key);
        settlement.setStatus(SettlementStatus.PAID);

        MerchantLedgerEntry payment = new MerchantLedgerEntry();
        payment.setId(UUID.randomUUID());
        payment.setMerchantId(settlement.getMerchantId());
        payment.setAmount(settlement.getAmount().negate());
        payment.setCurrency(settlement.getCurrency());
        payment.setEntryType(LedgerEntryType.SETTLEMENT);
        payment.setSourceReference("SETTLEMENT:" + settlement.getId());
        payment.setIdempotencyKey("SETTLEMENT_PAYMENT:" + settlement.getId());
        payment.setReason("Externally confirmed merchant settlement");
        payment.setSettlementId(settlement.getId());
        payment.setSettlementEligible(false);
        payment.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        ledger.save(payment);
        return view(settlements.save(settlement));
    }

    private MerchantSettlementDtos.View view(MerchantSettlement settlement) {
        List<UUID> ids = settlementEntries.findBySettlementId(settlement.getId()).stream()
            .map(MerchantSettlementEntry::getLedgerEntryId).sorted().toList();
        return view(settlement, ids);
    }

    private static MerchantSettlementDtos.View view(MerchantSettlement settlement, List<UUID> ids) {
        return new MerchantSettlementDtos.View(settlement.getId(), settlement.getMerchantId(),
            List.copyOf(ids), settlement.getAmount(), settlement.getCurrency(), settlement.getCutoffAt(),
            settlement.getStatus().name(), settlement.getCalculatedBy(), settlement.getCalculatedAt(),
            settlement.getApprovedBy(), settlement.getApprovedAt(), settlement.getApprovalNote(),
            settlement.getPaidBy(), settlement.getPaidAt(), settlement.getExternalPaymentReference());
    }

    private static void validate(MerchantSettlementDtos.Calculate request) {
        if (request == null || request.merchantId() == null || request.cutoffAt() == null
            || request.currency() == null || !request.currency().matches("^[A-Z]{3}$")
            || request.cutoffAt().isAfter(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5)))
            throw validation("Settlement calculation is incomplete or invalid.");
    }

    private static void validate(MerchantSettlementDtos.RecordPayment request) {
        if (request == null || !request.confirmExternalPayment() || request.paidAt() == null
            || request.externalReference() == null || request.externalReference().isBlank()
            || request.externalReference().length() > 200)
            throw validation("Settlement payment evidence is incomplete or invalid.");
    }

    private static boolean validPositiveMoney(BigDecimal value) {
        return value != null && value.signum() > 0 && value.scale() <= 2
            && value.precision() - value.scale() <= 10;
    }

    private static LocalDateTime micros(LocalDateTime value) {
        return value.withNano((value.getNano() / 1_000) * 1_000);
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validateKey(String key) {
        if (key == null || !key.matches("^[A-Za-z0-9._:-]{16,128}$"))
            throw validation("Idempotency-Key must contain 16 to 128 permitted characters.");
    }

    private static PaymentProblem validation(String message) {
        return new PaymentProblem(400, "VALIDATION_FAILED", message);
    }

    private static PaymentProblem notFound(String message) {
        return new PaymentProblem(404, "NOT_FOUND", message);
    }
}
