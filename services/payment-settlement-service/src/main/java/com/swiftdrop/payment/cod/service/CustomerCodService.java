package com.swiftdrop.payment.cod.service;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Verified customer delivery-fee receipts. No merchant ledger or PayHere payment is created. */
@Service
public class CustomerCodService {
    private final JdbcTemplate db;
    private final CodAmountVerifier verifier;
    private final JsonMapper json;

    public CustomerCodService(JdbcTemplate db, CodAmountVerifier verifier, JsonMapper json) {
        this.db = db; this.verifier = verifier; this.json = json;
    }

    @Transactional
    public void record(CodCollectedEvent event) {
        if (event == null || !"customer-cod.collected".equals(event.eventType()) || event.data() == null
                || event.data().merchantId() != null)
            throw new InvalidCodEvent("Expected a customer cash collection.");
        var d = event.data();
        CodCollectionService.validate(event, true);
        UUID booking;
        try { booking = UUID.fromString(d.bookingId()); }
        catch (IllegalArgumentException invalid) { throw new InvalidCodEvent("Invalid booking UUID."); }
        if (d.expectedAmount().compareTo(d.collectedAmount()) != 0)
            throw new InvalidCodEvent("The full customer delivery fee must be collected.");
        String fingerprint = json.writeValueAsString(List.of(booking, d.trackingNumber(), d.pricingSnapshotId(),
                d.courierId(), d.deliveryAttemptId(), d.collectedAmount().stripTrailingZeros().toPlainString(),
                d.currency(), d.collectedAt().toInstant()));
        var existing = db.queryForList("SELECT fingerprint FROM payment_settlement.customer_cod_receipts WHERE event_id=? OR booking_id=? OR delivery_attempt_id=?",
                event.eventId(), booking, d.deliveryAttemptId());
        if (!existing.isEmpty()) { checkReplay(existing, fingerprint); return; }
        UUID customer = verifier.verifyCustomer(d);
        if (customer == null) throw new InvalidCodEvent("Customer verification returned no owner.");
        UUID receipt = UUID.randomUUID();
        int inserted = db.update("""
            INSERT INTO payment_settlement.customer_cod_receipts
            (id,event_id,booking_id,customer_id,courier_id,delivery_attempt_id,tracking_number,pricing_snapshot_id,amount,currency,collected_at,fingerprint)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
            """, receipt, event.eventId(), booking, customer, d.courierId(), d.deliveryAttemptId(),
                d.trackingNumber(), d.pricingSnapshotId(), d.collectedAmount(), d.currency(), d.collectedAt(), fingerprint);
        if (inserted == 0) {
            checkReplay(db.queryForList("SELECT fingerprint FROM payment_settlement.customer_cod_receipts WHERE event_id=? OR booking_id=? OR delivery_attempt_id=?",
                    event.eventId(), booking, d.deliveryAttemptId()), fingerprint);
            return;
        }
        UUID eventId = UUID.randomUUID();
        var data = Map.of("bookingId", booking, "customerId", customer, "receiptId", receipt,
                "pricingSnapshotId", d.pricingSnapshotId(), "amount", d.collectedAmount(), "currency", d.currency(),
                "collectedAt", d.collectedAt(), "status", "PAID");
        String payload = json.writeValueAsString(Map.of("eventId", eventId, "eventType", "payment.customer-cod-collected",
                "version", 1, "producer", "payment-settlement-service", "occurredAt", Instant.now(), "data", data));
        db.update("""
            INSERT INTO payment_settlement.payment_outbox(id,event_type,payload,created_at,next_attempt_at)
            VALUES (?,'payment.customer-cod-collected',?,timezone('UTC',now()),timezone('UTC',now()))
            """, eventId, payload);
    }

    private static void checkReplay(List<Map<String,Object>> rows, String fingerprint) {
        if (rows.size() != 1 || !fingerprint.equals(rows.getFirst().get("fingerprint")))
            throw new InvalidCodEvent("Conflicting customer cash collection replay.");
    }
}
