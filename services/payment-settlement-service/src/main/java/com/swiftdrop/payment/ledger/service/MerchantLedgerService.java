package com.swiftdrop.payment.ledger.service;

import com.swiftdrop.payment.ledger.dto.MerchantLedgerDtos;
import com.swiftdrop.payment.ledger.entity.*;
import com.swiftdrop.payment.ledger.repository.MerchantLedgerEntryRepository;
import com.swiftdrop.payment.onlinepayment.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantLedgerService {
    private final MerchantLedgerEntryRepository entries;

    public MerchantLedgerService(MerchantLedgerEntryRepository entries) { this.entries = entries; }

    @Transactional(readOnly = true)
    public MerchantLedgerDtos.Page list(UUID requestedMerchant, String currency,
            Integer page, Integer size, Actor actor) {
        UUID merchantId = visibleMerchant(requestedMerchant, actor);
        String normalizedCurrency = normalizeCurrency(currency);
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? 20 : size;
        if (pageNumber < 0 || pageSize < 1 || pageSize > 100)
            throw validation("Invalid pagination.");
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        org.springframework.data.domain.Page<MerchantLedgerEntry> result =
            entries.findByMerchantIdAndCurrency(merchantId, normalizedCurrency, pageable);
        return new MerchantLedgerDtos.Page(result.getContent().stream().map(MerchantLedgerService::view).toList(),
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(),
            entries.balance(merchantId, normalizedCurrency), normalizedCurrency);
    }

    @Transactional
    public MerchantLedgerDtos.Entry adjust(MerchantLedgerDtos.Adjustment request, String key, Actor actor) {
        actor.requireAdmin();
        validateKey(key);
        validate(request);
        String source = request.sourceReference().trim();
        String reason = request.reason().trim();
        Optional<MerchantLedgerEntry> replay = entries.findByIdempotencyKey(key);
        if (replay.isPresent()) {
            MerchantLedgerEntry existing = replay.get();
            if (existing.getEntryType() != LedgerEntryType.ADJUSTMENT
                || !existing.getMerchantId().equals(request.merchantId())
                || existing.getAmount().compareTo(request.amount()) != 0
                || !existing.getCurrency().equals(request.currency())
                || !existing.getSourceReference().equals(source)
                || !Objects.equals(existing.getReason(), reason))
                throw PaymentProblem.conflict("Idempotency-Key was already used for another ledger command.");
            return view(existing);
        }
        if (entries.findByEntryTypeAndSourceReference(LedgerEntryType.ADJUSTMENT, source).isPresent())
            throw PaymentProblem.conflict("An adjustment already exists for this source reference.");

        MerchantLedgerEntry entry = new MerchantLedgerEntry();
        entry.setId(UUID.randomUUID());
        entry.setMerchantId(request.merchantId());
        entry.setAmount(request.amount());
        entry.setCurrency(request.currency());
        entry.setEntryType(LedgerEntryType.ADJUSTMENT);
        entry.setSourceReference(source);
        entry.setIdempotencyKey(key);
        entry.setReason(reason);
        entry.setSettlementEligible(true);
        entry.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return view(entries.save(entry));
    }

    private static UUID visibleMerchant(UUID requested, Actor actor) {
        if (actor.admin()) {
            if (requested == null) throw validation("merchantId is required for a dispatcher.");
            return requested;
        }
        if (!actor.merchant()) throw PaymentProblem.forbidden();
        if (requested != null && !actor.merchantId().equals(requested)) throw PaymentProblem.forbidden();
        return actor.merchantId();
    }

    private static void validate(MerchantLedgerDtos.Adjustment request) {
        if (request == null || request.merchantId() == null || !validSignedMoney(request.amount())
            || !normalizeCurrency(request.currency()).equals(request.currency())
            || blank(request.sourceReference(), 200) || blank(request.reason(), 500))
            throw validation("Ledger adjustment is incomplete or invalid.");
    }

    private static boolean validSignedMoney(BigDecimal amount) {
        return amount != null && amount.signum() != 0 && amount.scale() <= 2
            && amount.precision() - amount.scale() <= 10;
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || !currency.matches("^[A-Z]{3}$"))
            throw validation("currency must be a three-letter uppercase code.");
        return currency;
    }

    private static boolean blank(String value, int max) {
        return value == null || value.isBlank() || value.length() > max;
    }

    private static void validateKey(String key) {
        if (key == null || !key.matches("^[A-Za-z0-9._:-]{16,128}$"))
            throw validation("Idempotency-Key must contain 16 to 128 permitted characters.");
    }

    static MerchantLedgerDtos.Entry view(MerchantLedgerEntry entry) {
        return new MerchantLedgerDtos.Entry(entry.getId(), entry.getMerchantId(), entry.getAmount(),
            entry.getCurrency(), entry.getEntryType().name(), entry.getSourceReference(),
            entry.getIdempotencyKey(), entry.getReason(), entry.getSettlementId(),
            entry.isSettlementEligible(), entry.getCreatedAt().atOffset(ZoneOffset.UTC));
    }

    private static PaymentProblem validation(String message) {
        return new PaymentProblem(400, "VALIDATION_FAILED", message);
    }
}
