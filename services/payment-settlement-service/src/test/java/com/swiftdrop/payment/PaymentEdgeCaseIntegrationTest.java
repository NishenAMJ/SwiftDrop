package com.swiftdrop.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.payment.onlinepayment.dto.PaymentDtos.BookingClaimReleaseRequest;
import com.swiftdrop.payment.onlinepayment.dto.PaymentDtos.Checkout;
import com.swiftdrop.payment.onlinepayment.dto.PaymentDtos.Context;
import com.swiftdrop.payment.onlinepayment.dto.PaymentDtos.Create;
import com.swiftdrop.payment.onlinepayment.dto.PaymentDtos.Payer;
import com.swiftdrop.payment.onlinepayment.dto.PaymentDtos.Refund;
import com.swiftdrop.payment.onlinepayment.dto.PaymentDtos.RefundRequest;
import com.swiftdrop.payment.onlinepayment.dto.PaymentDtos.RefundResolutionRequest;
import com.swiftdrop.payment.onlinepayment.enums.PaymentStatus;
import com.swiftdrop.payment.onlinepayment.provider.PaymentProvider;
import com.swiftdrop.payment.onlinepayment.service.Actor;
import com.swiftdrop.payment.onlinepayment.service.PaymentProblem;
import com.swiftdrop.payment.onlinepayment.service.PaymentService;
import com.swiftdrop.payment.onlinepayment.service.PricingClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Failure, replay and boundary paths of online payments against a disposable PostgreSQL database.
 * The PayHere provider is always a mock: no test here can move real money.
 */
@SpringBootTest(properties = {"payment.online.enabled=true", "payment.workers.enabled=false", "payment.cod.consumer.enabled=false",
        "payment.cod.verification-mode=pending", "payment.cancellation.consumer.enabled=false", "spring.config.import=",
        "spring.flyway.create-schemas=true", "spring.rabbitmq.password=payment-test-only",
        "spring.rabbitmq.username=paymenttest", "spring.rabbitmq.host=localhost", "spring.rabbitmq.port=56785",
        "payment.payhere.merchant-id=test", "debug=false"})
@EnabledIfEnvironmentVariable(named = "PAYMENT_TEST_DB_URL", matches = ".+")
class PaymentEdgeCaseIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("PAYMENT_TEST_DB_URL"));
        p.add("spring.datasource.username", () -> "postgres");
        p.add("spring.datasource.password", () -> "payment-test-only");
    }

    @Autowired PaymentService service;
    @Autowired JdbcTemplate db;
    @MockitoBean PricingClient pricing;
    @MockitoBean PaymentProvider provider;
    @MockitoBean com.swiftdrop.payment.cod.service.CodAmountVerifier codVerifier;

    final UUID customerId = UUID.fromString("f2249d4c-a134-4bc0-b9ed-ffea97ad8791");
    final UUID merchantId = UUID.fromString("846c790e-497a-4340-8039-79e25c95f40e");
    final Actor customer = new Actor(customerId, null, Set.of("CUSTOMER"), "test");
    final Actor merchant = new Actor(UUID.randomUUID(), merchantId, Set.of("MERCHANT"), "test");
    final Actor admin = new Actor(UUID.fromString("a2249d4c-a134-4bc0-b9ed-ffea97ad8791"), null, Set.of("DISPATCHER"), "test");

    @BeforeEach
    void setup() {
        db.execute("TRUNCATE payment_settlement.customer_cod_receipts");
        db.execute("TRUNCATE payment_settlement.cancellation_inbox,payment_settlement.merchant_settlement_entries,"
                + "payment_settlement.cash_reconciliations,payment_settlement.cash_handover_items,"
                + "payment_settlement.merchant_ledger_entries,payment_settlement.merchant_settlements,"
                + "payment_settlement.cash_handovers,payment_settlement.cod_collections,payment_settlement.payment_callbacks,"
                + "payment_settlement.payment_transactions,payment_settlement.payment_outbox,"
                + "payment_settlement.payment_recovery_audit,payment_settlement.payment_booking_claims,"
                + "payment_settlement.payments CASCADE");
        when(pricing.lookup(anyString(), any())).thenAnswer(a -> new Context(a.getArgument(0), "MERCHANT_API", customerId,
                merchantId, true, UUID.randomUUID().toString(), new BigDecimal("1000.00"), "LKR",
                new Payer("Test", "Payer", "test@example.com", "0770000000", "Address", "Colombo", "Sri Lanka")));
        when(provider.checkout(any(), any()))
                .thenReturn(new Checkout("PAYHERE", "https://sandbox.payhere.lk/pay/checkout", "POST", Map.of("hash", "test")));
    }

    // ---------- helpers ----------

    UUID create(String booking, String key) {
        return UUID.fromString(service.create(new Create(booking), key, customer).get("id").toString());
    }

    void callback(UUID id, String reference, String amount, String currency, int status, char fingerprint) {
        when(provider.verify(anyMap())).thenReturn(new PaymentProvider.VerifiedCallback(id, reference,
                new BigDecimal(amount), currency, status, String.valueOf(fingerprint).repeat(64)));
        service.callback(Map.of());
    }

    UUID paid(String booking) {
        UUID id = create(booking, "payment-key-" + booking);
        callback(id, "123456", "1000", "LKR", 2, 'a');
        remote(id, "1000", "RECEIVED");
        return id;
    }

    void remote(UUID id, String amount, String status) {
        when(provider.retrieve(id)).thenReturn(List.of(
                new PaymentProvider.RemotePayment("123456", id.toString(), new BigDecimal(amount), "LKR", status)));
    }

    PaymentStatus status(UUID id) {
        return service.get(id, admin).status();
    }

    int outbox(String type) {
        return db.queryForObject("SELECT count(*) FROM payment_settlement.payment_outbox WHERE event_type=?", Integer.class, type);
    }

    String callbackOutcome(char fingerprint) {
        return db.queryForObject("SELECT outcome FROM payment_settlement.payment_callbacks WHERE fingerprint=?",
                String.class, String.valueOf(fingerprint).repeat(64));
    }

    static void assertProblem(int status, Executable call) {
        assertThatThrownBy(call::execute).isInstanceOfSatisfying(PaymentProblem.class,
                problem -> assertThat(problem.status).isEqualTo(status));
    }

    // ---------- history ----------

    @ParameterizedTest
    @CsvSource({"-1,20,'createdAt,desc'", "0,0,'createdAt,desc'", "0,101,'createdAt,desc'", "0,20,'amount,desc'"})
    void historyRejectsInvalidPaginationAndSort(int page, int size, String sort) {
        assertProblem(400, () -> service.history(customer, page, size, sort));
    }

    @Test
    void historyIsScopedToTheCallerAndHonoursAscendingSort() {
        UUID first = create("BOOK-H1", "payment-key-history-1");
        UUID second = create("BOOK-H2", "payment-key-history-2");

        assertThat(service.history(admin, 0, 100, "createdAt,asc").content()).extracting(v -> v.id())
                .containsExactly(first, second);
        assertThat(service.history(merchant, 0, 100, "createdAt,desc").totalElements()).isEqualTo(2);
        Actor otherMerchant = new Actor(UUID.randomUUID(), UUID.randomUUID(), Set.of("MERCHANT"), "test");
        assertThat(service.history(otherMerchant, 0, 100, "createdAt,desc").totalElements()).isZero();
        Actor merchantWithoutCompany = new Actor(UUID.randomUUID(), null, Set.of("MERCHANT"), "test");
        assertThat(service.history(merchantWithoutCompany, 0, 100, "createdAt,desc").totalElements()).isZero();
    }

    // ---------- create / checkout ----------

    @Test
    void createRejectsMalformedIdempotencyKeys() {
        assertProblem(400, () -> service.create(new Create("BOOK-K"), "short", customer));
        assertProblem(400, () -> service.create(new Create("BOOK-K"), null, customer));
        assertProblem(400, () -> service.create(new Create("BOOK-K"), "x".repeat(129), customer));
        assertProblem(400, () -> service.create(new Create("BOOK-K"), "has spaces in the key!", customer));
        assertThat(db.queryForObject("SELECT count(*) FROM payment_settlement.payments", Integer.class)).isZero();
    }

    @Test
    void replayingAPaidPaymentDoesNotOfferCheckoutAgain() {
        UUID id = paid("BOOK-R");
        Map<String, Object> replay = service.create(new Create("BOOK-R"), "payment-key-BOOK-R", customer);
        assertThat(replay.get("id").toString()).isEqualTo(id.toString());
        assertThat(replay).doesNotContainKey("checkout");
        assertProblem(409, () -> service.checkout(id, customer));
    }

    @Test
    void anotherCustomerCannotReplayOrCheckOutSomeoneElsesPayment() {
        UUID id = create("BOOK-O", "payment-key-owner-001");
        Actor stranger = new Actor(UUID.randomUUID(), null, Set.of("CUSTOMER"), "test");
        assertProblem(403, () -> service.checkout(id, stranger));
        assertProblem(404, () -> service.get(UUID.randomUUID(), admin));
    }

    // ---------- callbacks ----------

    @Test
    void callbackWithWrongAmountIsRejectedAndNotRecorded() {
        UUID id = create("BOOK-C1", "payment-key-callback-1");
        assertProblem(400, () -> callback(id, "123456", "999.99", "LKR", 2, 'a'));
        assertThat(status(id)).isEqualTo(PaymentStatus.PENDING);
        assertThat(db.queryForObject("SELECT count(*) FROM payment_settlement.payment_callbacks", Integer.class)).isZero();
        assertThat(outbox("payment.succeeded")).isZero();
    }

    @Test
    void callbackWithWrongCurrencyIsRejected() {
        UUID id = create("BOOK-C2", "payment-key-callback-2");
        assertProblem(400, () -> callback(id, "123456", "1000", "USD", 2, 'a'));
        assertThat(status(id)).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void callbackForUnknownPaymentIsNotFound() {
        assertProblem(404, () -> callback(UUID.randomUUID(), "123456", "1000", "LKR", 2, 'a'));
    }

    @Test
    void pendingStatusCallbackIsRecordedButIgnored() {
        UUID id = create("BOOK-C3", "payment-key-callback-3");
        callback(id, null, "1000", "LKR", 0, 'p');
        assertThat(status(id)).isEqualTo(PaymentStatus.PENDING);
        assertThat(callbackOutcome('p')).isEqualTo("IGNORED");
    }

    @Test
    void lateFailureAfterSuccessIsAConflictAndDoesNotDowngradeThePayment() {
        UUID id = paid("BOOK-C4");
        callback(id, "123456", "1000", "LKR", -2, 'f');
        assertThat(status(id)).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(callbackOutcome('f')).isEqualTo("CONFLICT_OR_REPLAY");
        assertThat(outbox("payment.failed")).isZero();
    }

    @Test
    void lateSuccessAfterTerminalFailureIsNotAppliedByTheCallback() {
        UUID id = create("BOOK-C5", "payment-key-callback-5");
        callback(id, null, "1000", "LKR", -1, 'f');
        callback(id, "123456", "1000", "LKR", 2, 's');
        assertThat(status(id)).isEqualTo(PaymentStatus.FAILED);
        assertThat(callbackOutcome('s')).isEqualTo("CONFLICT_OR_REPLAY");
        assertThat(outbox("payment.succeeded")).isZero();
    }

    @Test
    void chargebackCallbackOverridesSuccessOnceAndIsNeverTreatedAsARefund() {
        UUID id = paid("BOOK-C6");
        callback(id, "123456", "1000", "LKR", -3, 'c');
        callback(id, "123456", "1000", "LKR", -3, 'd');
        assertThat(status(id)).isEqualTo(PaymentStatus.CHARGEBACKED);
        assertThat(outbox("payment.chargebacked")).isEqualTo(1);
        assertThat(outbox("payment.refunded")).isZero();
        assertThat(callbackOutcome('d')).isEqualTo("CONFLICT_OR_REPLAY");
        assertProblem(409, () -> service.refund(id, new RefundRequest(new BigDecimal("10"), "After chargeback"),
                "refund-key-chargeback", admin));
    }

    @Test
    void successEventCarriesOwnershipForDownstreamConsumers() {
        paid("BOOK-C7");
        String payload = db.queryForObject(
                "SELECT payload FROM payment_settlement.payment_outbox WHERE event_type='payment.succeeded'", String.class);
        assertThat(payload).contains(customerId.toString()).contains(merchantId.toString()).contains("BOOK-C7");
    }

    // ---------- refunds ----------

    @ParameterizedTest
    @CsvSource(value = {"NULL,Reason", "0,Reason", "-1,Reason", "10.001,Reason", "10,NULL", "10,'   '"}, nullValues = "NULL")
    void refundRejectsInvalidAmountsAndReasons(String amount, String reason) {
        UUID id = paid("BOOK-V");
        assertProblem(400, () -> service.refund(id, new RefundRequest(amount == null ? null : new BigDecimal(amount), reason),
                "refund-key-validation", admin));
        verify(provider, never()).refund(anyString(), any(), anyString());
    }

    @Test
    void refundRejectsOverlongReasonAndBadKey() {
        UUID id = paid("BOOK-V2");
        assertProblem(400, () -> service.refund(id, new RefundRequest(BigDecimal.TEN, "r".repeat(501)), "refund-key-validation", admin));
        assertProblem(400, () -> service.refund(id, new RefundRequest(BigDecimal.TEN, "ok"), "bad key", admin));
    }

    @Test
    void reusingARefundKeyForDifferentDetailsIsAConflict() {
        UUID id = paid("BOOK-K1");
        when(provider.refund(anyString(), any(), anyString())).thenReturn(new PaymentProvider.RefundResult("SUCCESS", "987654"));
        service.refund(id, new RefundRequest(new BigDecimal("100.00"), "Adjustment"), "refund-key-reuse-01", admin);

        assertProblem(409, () -> service.refund(id, new RefundRequest(new BigDecimal("150.00"), "Adjustment"), "refund-key-reuse-01", admin));
        assertProblem(409, () -> service.refund(id, new RefundRequest(new BigDecimal("100.00"), "Different"), "refund-key-reuse-01", admin));
        assertThat(db.queryForObject("SELECT count(*) FROM payment_settlement.payment_transactions", Integer.class)).isEqualTo(1);
    }

    @Test
    void pendingPaymentCannotBeRefunded() {
        UUID id = create("BOOK-K2", "payment-key-refund-pending");
        assertProblem(409, () -> service.refund(id, new RefundRequest(BigDecimal.TEN, "Too early"), "refund-key-pending-1", admin));
    }

    @Test
    void refundOfExactRemainingBalanceIsAllowed() {
        UUID id = paid("BOOK-K3");
        when(provider.refund(anyString(), any(), anyString())).thenReturn(new PaymentProvider.RefundResult("SUCCESS", "987654"));
        Refund refund = service.refund(id, new RefundRequest(new BigDecimal("1000.00"), "Full refund"), "refund-key-full-0001", admin);
        assertThat(refund.status()).isEqualTo("SUCCESS");
        assertThat(status(id)).isEqualTo(PaymentStatus.REFUNDED);
        assertProblem(409, () -> service.refund(id, new RefundRequest(new BigDecimal("0.01"), "More"), "refund-key-full-0002", admin));
    }

    @Test
    void failedProviderPreflightLeavesTheRefundUncertainWithoutCallingRefund() {
        UUID id = paid("BOOK-P1");
        remote(id, "999", "RECEIVED");
        Refund refund = service.refund(id, new RefundRequest(new BigDecimal("100"), "Preflight"), "refund-key-preflight", admin);
        assertThat(refund.status()).isEqualTo("PENDING");
        verify(provider, never()).refund(anyString(), any(), anyString());
        assertThat(db.queryForObject("SELECT submitted_at IS NOT NULL FROM payment_settlement.payment_transactions WHERE id=?",
                Boolean.class, refund.id())).isTrue();
    }

    @Test
    void unknownProviderOutcomeKeepsTheReturnedReferenceForInvestigation() {
        UUID id = paid("BOOK-P2");
        when(provider.refund(anyString(), any(), anyString())).thenReturn(new PaymentProvider.RefundResult("UNKNOWN", "555000"));
        Refund refund = service.refund(id, new RefundRequest(new BigDecimal("100"), "Unknown"), "refund-key-unknown-1", admin);
        assertThat(refund.status()).isEqualTo("PENDING");
        assertThat(db.queryForObject("SELECT provider_reference FROM payment_settlement.payment_transactions WHERE id=?",
                String.class, refund.id())).isEqualTo("555000");
    }

    @Test
    void submittingAMissingOrCompletedRefundIsANoOp() {
        service.submitRefund(UUID.randomUUID());
        UUID id = paid("BOOK-P3");
        when(provider.refund(anyString(), any(), anyString())).thenReturn(new PaymentProvider.RefundResult("SUCCESS", "987654"));
        Refund refund = service.refund(id, new RefundRequest(new BigDecimal("100"), "Done"), "refund-key-noop-0001", admin);
        service.submitRefund(refund.id());
        verify(provider, org.mockito.Mockito.times(1)).refund(anyString(), any(), anyString());
    }

    // ---------- retry / resolve ----------

    @Test
    void retryRequiresConfirmationAnExistingRefundAndAnUncertainState() {
        UUID id = paid("BOOK-Y1");
        assertProblem(400, () -> service.retryRefund(id, UUID.randomUUID(), false, admin));
        assertProblem(404, () -> service.retryRefund(id, UUID.randomUUID(), true, admin));
        assertProblem(403, () -> service.retryRefund(id, UUID.randomUUID(), true, customer));

        when(provider.refund(anyString(), any(), anyString())).thenReturn(new PaymentProvider.RefundResult("SUCCESS", "987654"));
        Refund done = service.refund(id, new RefundRequest(new BigDecimal("100"), "Done"), "refund-key-retry-001", admin);
        assertProblem(409, () -> service.retryRefund(id, done.id(), true, admin));
    }

    @Test
    void retryIsBlockedWhenPayHereReportsSeveralMatches() {
        UUID id = paid("BOOK-Y2");
        when(provider.refund(anyString(), any(), anyString())).thenReturn(new PaymentProvider.RefundResult("UNKNOWN", null));
        Refund pending = service.refund(id, new RefundRequest(new BigDecimal("100"), "Uncertain"), "refund-key-retry-002", admin);
        var match = new PaymentProvider.RemotePayment("123456", id.toString(), new BigDecimal("1000"), "LKR", "RECEIVED");
        when(provider.retrieve(id)).thenReturn(List.of(match, match));
        assertProblem(409, () -> service.retryRefund(id, pending.id(), true, admin));
        verify(provider, org.mockito.Mockito.times(1)).refund(anyString(), any(), anyString());
    }

    @Test
    void resolutionValidatesEvidenceBeforeTouchingTheRefund() {
        UUID id = paid("BOOK-Z1");
        UUID refund = UUID.randomUUID();
        var valid = new RefundResolutionRequest("CONFIRMED_SUCCESS", "PAYHERE_DASHBOARD", "row-1", "987654", "Seen", true);
        assertProblem(400, () -> service.resolveUncertainRefund(id, refund, null, admin));
        assertProblem(400, () -> service.resolveUncertainRefund(id, refund,
                new RefundResolutionRequest("MAYBE", "PAYHERE_DASHBOARD", "row-1", "987654", "Seen", true), admin));
        assertProblem(400, () -> service.resolveUncertainRefund(id, refund,
                new RefundResolutionRequest("CONFIRMED_SUCCESS", "EMAIL", "row-1", "987654", "Seen", true), admin));
        assertProblem(400, () -> service.resolveUncertainRefund(id, refund,
                new RefundResolutionRequest("CONFIRMED_SUCCESS", "PAYHERE_DASHBOARD", " ", "987654", "Seen", true), admin));
        assertProblem(400, () -> service.resolveUncertainRefund(id, refund,
                new RefundResolutionRequest("CONFIRMED_SUCCESS", "PAYHERE_DASHBOARD", "row-1", "987654", "Seen", false), admin));
        assertProblem(400, () -> service.resolveUncertainRefund(id, refund,
                new RefundResolutionRequest("CONFIRMED_SUCCESS", "PAYHERE_DASHBOARD", "row-1", "REF-ABC", "Seen", true), admin));
        assertProblem(400, () -> service.resolveUncertainRefund(id, refund,
                new RefundResolutionRequest("CONFIRMED_SUCCESS", "PAYHERE_DASHBOARD", "row-1", null, "Seen", true), admin));
        assertProblem(400, () -> service.resolveUncertainRefund(id, refund,
                new RefundResolutionRequest("CONFIRMED_FAILURE", "PAYHERE_DASHBOARD", "row-1", "987654", "Seen", true), admin));
        assertProblem(404, () -> service.resolveUncertainRefund(id, refund, valid, admin));
        assertProblem(403, () -> service.resolveUncertainRefund(id, refund, valid, customer));
        assertThat(db.queryForObject("SELECT count(*) FROM payment_settlement.payment_recovery_audit", Integer.class)).isZero();
    }

    @Test
    void confirmingTheFullAmountMarksThePaymentRefunded() {
        UUID id = paid("BOOK-Z2");
        when(provider.refund(anyString(), any(), anyString())).thenReturn(new PaymentProvider.RefundResult("UNKNOWN", null));
        Refund pending = service.refund(id, new RefundRequest(new BigDecimal("1000"), "Full"), "refund-key-resolve-01", admin);
        service.resolveUncertainRefund(id, pending.id(), new RefundResolutionRequest("CONFIRMED_SUCCESS", "PAYHERE_DASHBOARD",
                " row-77 ", "987654", " Confirmed ", true), admin);
        assertThat(status(id)).isEqualTo(PaymentStatus.REFUNDED);
        var audit = service.recoveryAudit(id, admin).getFirst();
        assertThat(audit.evidenceReference()).isEqualTo("row-77");
        assertThat(audit.reason()).isEqualTo("Confirmed");
    }

    // ---------- booking claim release ----------

    @Test
    void bookingClaimReleaseValidatesRequestAndPaymentState() {
        UUID id = paid("BOOK-B1");
        var valid = new BookingClaimReleaseRequest(true, "PAYHERE_DASHBOARD", "dash-1", "Terminal");
        assertProblem(400, () -> service.releaseFailedBookingClaim(id, null, admin));
        assertProblem(400, () -> service.releaseFailedBookingClaim(id,
                new BookingClaimReleaseRequest(false, "PAYHERE_DASHBOARD", "dash-1", "Terminal"), admin));
        assertProblem(400, () -> service.releaseFailedBookingClaim(id,
                new BookingClaimReleaseRequest(true, "PHONE_CALL", "dash-1", "Terminal"), admin));
        assertProblem(400, () -> service.releaseFailedBookingClaim(id,
                new BookingClaimReleaseRequest(true, "PAYHERE_DASHBOARD", "d".repeat(201), "Terminal"), admin));
        assertProblem(400, () -> service.releaseFailedBookingClaim(id,
                new BookingClaimReleaseRequest(true, "PAYHERE_DASHBOARD", "dash-1", ""), admin));
        assertProblem(403, () -> service.releaseFailedBookingClaim(id, valid, customer));
        assertProblem(409, () -> service.releaseFailedBookingClaim(id, valid, admin));
    }

    @Test
    void bookingClaimCannotBeReleasedTwice() {
        UUID id = create("BOOK-B2", "payment-key-release-02");
        callback(id, null, "1000", "LKR", -1, 'f');
        when(provider.retrieve(id)).thenReturn(List.of());
        var request = new BookingClaimReleaseRequest(true, "PAYHERE_SUPPORT_CASE", "case-1", "Terminal decline");
        service.releaseFailedBookingClaim(id, request, admin);
        assertProblem(409, () -> service.releaseFailedBookingClaim(id, request, admin));
        assertThat(service.recoveryAudit(id, admin)).hasSize(1);
    }

    @Test
    void recoveryAuditIsDispatcherOnlyAndRequiresAnExistingPayment() {
        UUID id = create("BOOK-A1", "payment-key-audit-001");
        assertProblem(403, () -> service.recoveryAudit(id, customer));
        assertProblem(404, () -> service.recoveryAudit(UUID.randomUUID(), admin));
        assertThat(service.recoveryAudit(id, admin)).isEmpty();
    }

    // ---------- reconciliation ----------

    @Test
    void reconciliationIgnoresMissingOrDuplicateProviderMatches() {
        UUID id = create("BOOK-X1", "payment-key-reconcile-1");
        when(provider.retrieve(id)).thenReturn(List.of());
        service.reconcile(id);
        var match = new PaymentProvider.RemotePayment("123456", id.toString(), new BigDecimal("1000"), "LKR", "RECEIVED");
        when(provider.retrieve(id)).thenReturn(List.of(match, match));
        service.reconcile(id);
        assertThat(status(id)).isEqualTo(PaymentStatus.PENDING);
        assertThat(outbox("payment.succeeded")).isZero();
    }

    @Test
    void reconciliationIgnoresProviderRecordsWithMismatchedDetails() {
        UUID id = create("BOOK-X2", "payment-key-reconcile-2");
        remote(id, "500", "RECEIVED");
        service.reconcile(id);
        when(provider.retrieve(id)).thenReturn(List.of(
                new PaymentProvider.RemotePayment("123456", UUID.randomUUID().toString(), new BigDecimal("1000"), "LKR", "RECEIVED")));
        service.reconcile(id);
        when(provider.retrieve(id)).thenReturn(List.of(
                new PaymentProvider.RemotePayment("123456", id.toString(), new BigDecimal("1000"), "USD", "RECEIVED")));
        service.reconcile(id);
        assertThat(status(id)).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void reconciliationRecoversAFailedPaymentThatPayHereActuallyReceived() {
        UUID id = create("BOOK-X3", "payment-key-reconcile-3");
        callback(id, null, "1000", "LKR", -1, 'f');
        remote(id, "1000", "RECEIVED");
        service.reconcile(id);
        assertThat(status(id)).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void reconciliationAppliesAChargebackOnce() {
        UUID id = paid("BOOK-X4");
        remote(id, "1000", "CHARGEBACKED");
        service.reconcile(id);
        service.reconcile(id);
        assertThat(status(id)).isEqualTo(PaymentStatus.CHARGEBACKED);
        assertThat(outbox("payment.chargebacked")).isEqualTo(1);
    }

    @Test
    void reconciliationIgnoresAProviderRecordWithADifferentReference() {
        UUID id = paid("BOOK-X5");
        when(provider.retrieve(id)).thenReturn(List.of(
                new PaymentProvider.RemotePayment("999999", id.toString(), new BigDecimal("1000"), "LKR", "CHARGEBACKED")));
        service.reconcile(id);
        assertThat(status(id)).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void reconciliationConfirmsAWholePaymentRefundThatWasLeftUncertain() {
        UUID id = paid("BOOK-X6");
        when(provider.refund(anyString(), any(), anyString())).thenReturn(new PaymentProvider.RefundResult("UNKNOWN", null));
        Refund pending = service.refund(id, new RefundRequest(new BigDecimal("1000"), "Full"), "refund-key-reconcile-1", admin);
        remote(id, "1000", "REFUNDED");
        service.reconcile(id);
        assertThat(status(id)).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(service.refundView(pending.id(), id, admin).status()).isEqualTo("SUCCESS");
        assertThat(db.queryForObject("SELECT reserved_amount FROM payment_settlement.payments WHERE id=?", BigDecimal.class, id).signum())
                .isZero();
        assertThat(outbox("payment.refunded")).isEqualTo(1);
    }

    @Test
    void reconciliationDoesNotTreatAnAggregateRefundAsProofOfAPartialRefund() {
        UUID id = paid("BOOK-X7");
        when(provider.refund(anyString(), any(), anyString())).thenReturn(new PaymentProvider.RefundResult("UNKNOWN", null));
        Refund pending = service.refund(id, new RefundRequest(new BigDecimal("300"), "Partial"), "refund-key-reconcile-2", admin);
        remote(id, "1000", "REFUNDED");
        service.reconcile(id);
        assertThat(status(id)).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(service.refundView(pending.id(), id, admin).status()).isEqualTo("PENDING");
    }
}
