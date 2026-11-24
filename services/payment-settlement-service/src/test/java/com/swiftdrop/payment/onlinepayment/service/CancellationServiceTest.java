package com.swiftdrop.payment.onlinepayment.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CancellationServiceTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final PaymentService payments = mock(PaymentService.class);
    private final CancellationService service = new CancellationService(db, tx, JsonMapper.builder().build(), payments);
    private final UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach void transactions() {
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(tx).executeWithoutResult(any());
    }

    private void pending(String method) {
        String payment = method.equals("ONLINE")
                ? "\"paymentId\":\"00000000-0000-0000-0000-000000000004\"," : "";
        String payload = """
            {"eventId":"00000000-0000-0000-0000-000000000001","eventType":"parcel.cancelled",
             "version":1,"producer":"parcel-pricing-service","occurredAt":"2026-11-22T00:00:00Z",
             "data":{"parcelId":"00000000-0000-0000-0000-000000000002",
             "customerUserId":"00000000-0000-0000-0000-000000000003",
             %s"paymentMethod":"%s","deliveryStatus":"CANCELLED",
             "cancelledAt":"2026-11-22T00:00:00Z","cancellationFee":0,"refundAmount":0}}
            """.formatted(payment, method);
        Map<String, Object> row = new HashMap<>();
        row.put("event_id", eventId);
        row.put("payload", payload);
        when(db.queryForList(anyString())).thenReturn(List.of(row));
    }

    @Test void codCompletesWithoutAnyOnlinePaymentOperation() {
        pending("COD");
        service.processNext();
        verify(db).update(startsWith("UPDATE payment_settlement.cancellation_inbox SET status="),
                eq("COMPLETED"), isNull(), eq(eventId));
        verifyNoInteractions(payments);
    }

    @Test void unavailableOnlineRefundRemainsPendingAndNeverClaimsSuccess() {
        pending("ONLINE");
        when(payments.reserveCancellation(any())).thenThrow(
                new PaymentProblem(503, "ONLINE_PAYMENTS_DISABLED", "Online refunds are unavailable."));
        service.processNext();
        verify(db).update(startsWith("UPDATE payment_settlement.cancellation_inbox SET status="),
                eq("PENDING"), eq("Online refunds are unavailable."), eq(eventId));
        verify(db, never()).update(startsWith("UPDATE payment_settlement.cancellation_inbox SET status="),
                eq("COMPLETED"), any(), any());
    }

    @Test void conflictingOnlinePaymentRequiresReview() {
        pending("ONLINE");
        when(payments.reserveCancellation(any())).thenThrow(PaymentProblem.conflict("Payment does not match."));
        service.processNext();
        verify(db).update(startsWith("UPDATE payment_settlement.cancellation_inbox SET status="),
                eq("ATTENTION_REQUIRED"), eq("Payment does not match."), eq(eventId));
    }
}
