package com.swiftdrop.payment;

import com.swiftdrop.payment.cod.service.*;
import com.swiftdrop.payment.ledger.dto.MerchantLedgerDtos;
import com.swiftdrop.payment.ledger.service.MerchantLedgerService;
import com.swiftdrop.payment.onlinepayment.service.*;
import com.swiftdrop.payment.reconciliation.dto.CashHandoverDtos;
import com.swiftdrop.payment.reconciliation.service.CashHandoverService;
import com.swiftdrop.payment.settlement.dto.MerchantSettlementDtos;
import com.swiftdrop.payment.settlement.service.MerchantSettlementService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"payment.online.enabled=true", "payment.workers.enabled=false", "payment.cod.consumer.enabled=false",
    "payment.cancellation.consumer.enabled=false", "spring.rabbitmq.host=localhost", "spring.rabbitmq.port=56785",
    "spring.rabbitmq.username=paymenttest", "spring.rabbitmq.password=payment-test-only", "spring.config.import=", "spring.flyway.create-schemas=true", "debug=false"})
@EnabledIfEnvironmentVariable(named = "PAYMENT_TEST_DB_URL", matches = ".+")
class Member9IntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PAYMENT_TEST_DB_URL"));
        properties.add("spring.datasource.username", () -> "postgres");
        properties.add("spring.datasource.password", () -> "payment-test-only");
    }

    @Autowired CodCollectionService cod;
    @Autowired CashHandoverService handovers;
    @Autowired MerchantLedgerService ledger;
    @Autowired MerchantSettlementService settlements;
    @Autowired JdbcTemplate db;
    @MockitoBean CodAmountVerifier verifier;

    private final UUID merchantId = UUID.fromString("846c790e-497a-4340-8039-79e25c95f40e");
    private final UUID courierId = UUID.fromString("aa6c790e-497a-4340-8039-79e25c95f40e");
    private final Actor courier = new Actor(courierId, null, Set.of("COURIER"), "test");
    private final Actor dispatcher = new Actor(UUID.fromString("bb6c790e-497a-4340-8039-79e25c95f40e"),
        null, Set.of("DISPATCHER"), "test");

    @BeforeEach
    void clean() {
        db.execute("TRUNCATE payment_settlement.merchant_settlement_entries,payment_settlement.cash_reconciliations,"+
            "payment_settlement.cash_handover_items,payment_settlement.merchant_ledger_entries,"+
            "payment_settlement.merchant_settlements,payment_settlement.cash_handovers,"+
            "payment_settlement.cod_collections CASCADE");
    }

    @Test
    void duplicateCodEventCreatesOneCollectionAndLedgerEntry() {
        CodCollectedEvent event = event("000001", new BigDecimal("1000.00"));
        UUID first = cod.record(event).getId();
        UUID replay = cod.record(event).getId();

        assertEquals(first, replay);
        assertEquals(1, count("cod_collections"));
        assertEquals(1, count("merchant_ledger_entries"));
        verify(verifier, times(1)).verify(event.data());
    }

    @Test
    void reconciliationReportsExactShortAndExcessAndOnlyExactBecomesEligible() {
        assertReconciliation("000011", "1000.00", "EXACT", "CONFIRMED", true, null);
        assertReconciliation("000012", "900.00", "SHORT", "FLAGGED", false, "Cash shortage under review");
        assertReconciliation("000013", "1100.00", "EXCESS", "FLAGGED", false, "Cash excess under review");
    }

    @Test
    void postedLedgerFinancialFieldsCannotBeChangedOrDeleted() {
        UUID collectionId = cod.record(event("000021", new BigDecimal("1000.00"))).getId();
        UUID entryId = db.queryForObject(
            "SELECT id FROM payment_settlement.merchant_ledger_entries WHERE source_reference=?",
            UUID.class, "COD_COLLECTION:" + collectionId);

        assertTrue(assertThrows(DataAccessException.class, () -> db.update(
            "UPDATE payment_settlement.merchant_ledger_entries SET amount=999 WHERE id=?", entryId)).getMostSpecificCause().getMessage().contains("immutable"));
        assertTrue(assertThrows(DataAccessException.class, () -> db.update(
            "DELETE FROM payment_settlement.merchant_ledger_entries WHERE id=?", entryId)).getMostSpecificCause().getMessage().contains("cannot be deleted"));
        assertEquals(new BigDecimal("1000.00"), db.queryForObject(
            "SELECT amount FROM payment_settlement.merchant_ledger_entries WHERE id=?", BigDecimal.class, entryId));
    }

    @Test
    void settlementUsesSignedEligibleEntriesAndRecordsExternalPaymentSeparately() {
        var event = event("000031", new BigDecimal("1000.00"));
        UUID collectionId = cod.record(event).getId();
        handovers.create(new CashHandoverDtos.Create(List.of(collectionId),
            new BigDecimal("1000.00"), "LKR"), "handover-key-0031", courier);
        UUID handoverId = db.queryForObject("SELECT id FROM payment_settlement.cash_handovers", UUID.class);
        handovers.confirm(handoverId, new CashHandoverDtos.Confirm(new BigDecimal("1000.00"), null),
            "confirm-key-0031", dispatcher);
        ledger.adjust(new MerchantLedgerDtos.Adjustment(merchantId, new BigDecimal("-100.00"), "LKR",
            "charge-adjustment-31", "Agreed delivery charge"), "adjustment-key-31", dispatcher);

        MerchantSettlementDtos.View calculated = settlements.calculate(new MerchantSettlementDtos.Calculate(
            merchantId, "LKR", OffsetDateTime.now(ZoneOffset.UTC)), "settlement-key-31", dispatcher);
        assertEquals(new BigDecimal("900.00"), calculated.amount());
        assertEquals(2, calculated.ledgerEntryIds().size());
        MerchantSettlementDtos.View approved = settlements.approve(calculated.id(),
            new MerchantSettlementDtos.Approve(true, "Reviewed"), "approval-key-0031", dispatcher);
        MerchantSettlementDtos.View paid = settlements.recordPayment(calculated.id(),
            new MerchantSettlementDtos.RecordPayment("bank-transfer-31",
                approved.approvedAt().plusSeconds(1), true), "payment-key-0031", dispatcher);

        assertEquals("PAID", paid.status());
        assertEquals(new BigDecimal("-900.00"), db.queryForObject(
            "SELECT amount FROM payment_settlement.merchant_ledger_entries WHERE entry_type='SETTLEMENT'",
            BigDecimal.class));
        assertEquals(0, db.queryForObject(
            "SELECT COALESCE(SUM(amount),0) FROM payment_settlement.merchant_ledger_entries WHERE merchant_id=?",
            BigDecimal.class, merchantId).compareTo(BigDecimal.ZERO));
    }

    @Test
    void merchantCannotReadAnotherMerchantsLedger() {
        Actor merchant = new Actor(UUID.randomUUID(), merchantId, Set.of("MERCHANT"), "test");
        UUID other = UUID.randomUUID();
        assertThrows(PaymentProblem.class, () -> ledger.list(other, "LKR", 0, 20, merchant));
    }

    private void assertReconciliation(String suffix, String received, String reconciliationStatus,
            String handoverStatus, boolean eligible, String note) {
        UUID collectionId = cod.record(event(suffix, new BigDecimal("1000.00"))).getId();
        CashHandoverDtos.Handover handover = handovers.create(new CashHandoverDtos.Create(
            List.of(collectionId), new BigDecimal(received), "LKR"), "handover-key-" + suffix, courier);
        CashHandoverDtos.Handover confirmed = handovers.confirm(handover.id(),
            new CashHandoverDtos.Confirm(new BigDecimal(received), note),
            "confirm-key-" + suffix, dispatcher);
        assertEquals(reconciliationStatus, confirmed.reconciliation().status());
        assertEquals(handoverStatus, confirmed.status());
        assertEquals(eligible, db.queryForObject(
            "SELECT settlement_eligible FROM payment_settlement.merchant_ledger_entries WHERE source_reference=?",
            Boolean.class, "COD_COLLECTION:" + collectionId));
    }

    private CodCollectedEvent event(String suffix, BigDecimal collected) {
        return new CodCollectedEvent(UUID.randomUUID(), "delivery.cod-collected", 1,
            OffsetDateTime.now(ZoneOffset.UTC), "delivery-operations-service",
            new CodCollectedEvent.Data("b59b1f85-f260-4620-a6a8-f5ff72" + suffix,
                "SWD-2026-" + suffix, "SNP-" + suffix, merchantId, courierId, UUID.randomUUID(), "COD",
                new BigDecimal("1000.00"), collected, "LKR",
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1).truncatedTo(java.time.temporal.ChronoUnit.MICROS)));
    }

    private int count(String table) {
        return db.queryForObject("SELECT count(*) FROM payment_settlement." + table, Integer.class);
    }
}
