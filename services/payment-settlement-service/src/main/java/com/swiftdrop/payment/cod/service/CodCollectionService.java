package com.swiftdrop.payment.cod.service;

import com.swiftdrop.payment.cod.entity.*;
import com.swiftdrop.payment.cod.repository.CodCollectionRepository;
import com.swiftdrop.payment.ledger.entity.*;
import com.swiftdrop.payment.ledger.repository.MerchantLedgerEntryRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CodCollectionService {
    private static final String EVENT_TYPE = "delivery.cod-collected";
    private static final String PRODUCER = "delivery-operations-service";

    private final CodCollectionRepository collections;
    private final MerchantLedgerEntryRepository ledger;
    private final CodAmountVerifier verifier;

    public CodCollectionService(CodCollectionRepository collections,
            MerchantLedgerEntryRepository ledger, CodAmountVerifier verifier) {
        this.collections = collections;
        this.ledger = ledger;
        this.verifier = verifier;
    }

    @Transactional
    public CodCollection record(CodCollectedEvent event) {
        validate(event);
        Optional<CodCollection> replay = collections.findBySourceEventId(event.eventId());
        if (replay.isPresent()) {
            if (!sameCollection(replay.get(), event.data()))
                throw new InvalidCodEvent("A duplicate eventId contains conflicting COD collection data.");
            return replay.get();
        }

        verifier.verify(event.data());
        if (collections.findByBookingId(event.data().bookingId()).isPresent())
            throw new InvalidCodEvent("The booking already has a COD collection from another event.");

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        CodCollection collection = toCollection(event, now);
        MerchantLedgerEntry entry = new MerchantLedgerEntry();
        entry.setId(UUID.randomUUID());
        entry.setMerchantId(collection.getMerchantId());
        entry.setAmount(collection.getCollectedAmount());
        entry.setCurrency(collection.getCurrency());
        entry.setEntryType(LedgerEntryType.COD_COLLECTION);
        entry.setSourceReference("COD_COLLECTION:" + collection.getId());
        entry.setIdempotencyKey("COD_EVENT:" + event.eventId());
        entry.setSettlementEligible(false);
        entry.setCreatedAt(now);

        collections.save(collection);
        ledger.save(entry);
        return collection;
    }

    private static CodCollection toCollection(CodCollectedEvent event, LocalDateTime now) {
        CodCollectedEvent.Data data = event.data();
        CodCollection result = new CodCollection();
        result.setId(UUID.randomUUID());
        result.setBookingId(data.bookingId());
        result.setTrackingNumber(data.trackingNumber());
        result.setPricingSnapshotId(data.pricingSnapshotId());
        result.setMerchantId(data.merchantId());
        result.setCourierId(data.courierId());
        result.setDeliveryAttemptId(data.deliveryAttemptId());
        result.setExpectedAmount(data.expectedAmount());
        result.setCollectedAmount(data.collectedAmount());
        result.setCurrency(data.currency());
        result.setCollectedAt(data.collectedAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
        result.setStatus(CodCollectionStatus.RECORDED);
        result.setSourceEventId(event.eventId());
        result.setCreatedAt(now);
        return result;
    }

    private static boolean sameCollection(CodCollection existing, CodCollectedEvent.Data data) {
        return Objects.equals(existing.getBookingId(), data.bookingId())
            && Objects.equals(existing.getPricingSnapshotId(), data.pricingSnapshotId())
            && Objects.equals(existing.getMerchantId(), data.merchantId())
            && Objects.equals(existing.getCourierId(), data.courierId())
            && Objects.equals(existing.getDeliveryAttemptId(), data.deliveryAttemptId())
            && moneyEquals(existing.getExpectedAmount(), data.expectedAmount())
            && moneyEquals(existing.getCollectedAmount(), data.collectedAmount())
            && Objects.equals(existing.getCurrency(), data.currency());
    }

    private static boolean moneyEquals(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    static void validate(CodCollectedEvent event) {
        if (event == null || event.eventId() == null || event.occurredAt() == null
            || !EVENT_TYPE.equals(event.eventType()) || event.version() != 1
            || !PRODUCER.equals(event.producer()) || event.data() == null)
            throw new InvalidCodEvent("COD event envelope is incomplete or unsupported.");

        CodCollectedEvent.Data data = event.data();
        if (blank(data.bookingId(), 100) || blank(data.trackingNumber(), 40)
            || !data.trackingNumber().matches("^SWD-[0-9]{4}-[0-9]{6,}$")
            || blank(data.pricingSnapshotId(), 64) || data.merchantId() == null
            || data.courierId() == null || data.deliveryAttemptId() == null
            || !"COD".equals(data.paymentMethod()) || data.collectedAt() == null
            || data.collectedAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5))
            || !validMoney(data.expectedAmount()) || !validMoney(data.collectedAmount())
            || data.currency() == null || !data.currency().matches("^[A-Z]{3}$"))
            throw new InvalidCodEvent("COD event data is incomplete or invalid.");
    }

    private static boolean blank(String value, int max) {
        return value == null || value.isBlank() || value.length() > max;
    }

    private static boolean validMoney(BigDecimal value) {
        return value != null && value.signum() > 0 && value.scale() <= 2
            && value.precision() - value.scale() <= 10;
    }
}
