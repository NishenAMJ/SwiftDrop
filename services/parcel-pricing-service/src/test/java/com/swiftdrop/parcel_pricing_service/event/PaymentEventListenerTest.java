package com.swiftdrop.parcel_pricing_service.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.parcel_pricing_service.service.BookingService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import tools.jackson.databind.json.JsonMapper;

/** Routing, envelope validation and idempotency of payment events consumed by parcel-pricing. */
class PaymentEventListenerTest {
    private final EventInbox inbox = mock(EventInbox.class);
    private final BookingService bookings = mock(BookingService.class);
    private final PaymentEventListener listener = new PaymentEventListener(JsonMapper.builder().build(), inbox, bookings);
    private final UUID eventId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();
    private final String bookingId = UUID.randomUUID().toString();

    @BeforeEach
    void runEffectsOnce() {
        when(inbox.processOnce(any(), any(), any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        });
    }

    /** Amounts are compared numerically: the envelope may carry 550.00 or 550.0. */
    private static BigDecimal amount(String value) {
        return org.mockito.ArgumentMatchers.argThat(v -> v != null && v.compareTo(new BigDecimal(value)) == 0);
    }

    private Message message(String type, String producer, String data) {
        String body = """
            {"eventId":"%s","eventType":"%s","version":1,"producer":"%s","data":%s}
            """.formatted(eventId, type, producer, data);
        return new Message(body.getBytes(StandardCharsets.UTF_8));
    }

    private Message payment(String type, String extra) {
        return message(type, "payment-settlement-service",
                "{\"bookingId\":\"%s\",\"paymentId\":\"%s\"%s}".formatted(bookingId, paymentId, extra));
    }

    @Test
    void succeededPaymentConfirmsTheBooking() {
        listener.onMessage(payment("payment.succeeded", ",\"status\":\"SUCCESS\",\"amount\":550.00,\"currency\":\"LKR\""));
        verify(inbox).processOnce(eq(eventId), eq("payment.succeeded"), any());
        verify(bookings).confirmPaidFromEvent(eq(bookingId), eq(paymentId), amount("550.00"), eq("LKR"));
    }

    @Test
    void succeededEventWithAnotherStatusDoesNotConfirm() {
        listener.onMessage(payment("payment.succeeded", ",\"status\":\"PENDING\",\"amount\":550.00,\"currency\":\"LKR\""));
        verify(bookings, never()).confirmPaidFromEvent(any(), any(), any(), any());
    }

    @Test
    void failedPaymentIsRecorded() {
        listener.onMessage(payment("payment.failed", ",\"status\":\"FAILED\""));
        verify(bookings).markPaymentFailed(bookingId);
    }

    @Test
    void refundedPaymentPassesTheRefundedTotal() {
        listener.onMessage(payment("payment.refunded", ",\"status\":\"REFUNDED\",\"refundedAmount\":450.00"));
        verify(bookings).markRefunded(eq(bookingId), eq("REFUNDED"), eq(paymentId), amount("450.00"));
    }

    @Test
    void customerCashCollectionIsForwardedOnlyWhenPaid() {
        UUID customer = UUID.randomUUID();
        String cod = ",\"customerId\":\"%s\",\"pricingSnapshotId\":\"SNP-1\",\"amount\":550.00,\"currency\":\"LKR\",\"collectedAt\":\"2026-11-22T00:00:00Z\""
                .formatted(customer);
        listener.onMessage(payment("payment.customer-cod-collected", ",\"status\":\"PAID\"" + cod));
        verify(bookings).confirmCustomerCod(eq(bookingId), eq(customer), eq("SNP-1"), amount("550.00"), eq("LKR"), eq("2026-11-22T00:00:00Z"));

        assertThatThrownBy(() -> listener.onMessage(payment("payment.customer-cod-collected", ",\"status\":\"PENDING\"" + cod)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void unknownPaymentEventTypesAreAcknowledgedWithoutSideEffects() {
        listener.onMessage(payment("payment.chargebacked", ",\"status\":\"CHARGEBACKED\""));
        verifyNoInteractions(inbox, bookings);
    }

    @Test
    void eventsFromAnotherProducerAreDeadLettered() {
        assertThatThrownBy(() -> listener.onMessage(message("payment.succeeded", "evil-service",
                "{\"bookingId\":\"%s\",\"status\":\"SUCCESS\"}".formatted(bookingId))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(bookings);
    }

    @Test
    void eventsWithoutABookingAreDeadLettered() {
        assertThatThrownBy(() -> listener.onMessage(message("payment.failed", "payment-settlement-service", "{\"bookingId\":\" \"}")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(bookings);
    }

    @Test
    void malformedEnvelopesAreDeadLettered() {
        assertThatThrownBy(() -> listener.onMessage(new Message("{oops".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.onMessage(new Message(
                "{\"eventId\":\"not-a-uuid\",\"eventType\":\"payment.failed\",\"version\":1,\"data\":{}}".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.onMessage(new Message(
                ("{\"eventId\":\"" + eventId + "\",\"eventType\":\"payment.failed\",\"version\":2,\"data\":{}}").getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.onMessage(new Message(
                ("{\"eventId\":\"" + eventId + "\",\"eventType\":\"payment.failed\",\"version\":1,\"data\":[]}").getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(inbox, bookings);
    }

    @Test
    void nonNumericAmountsAndInvalidIdsAreNotGuessed() {
        listener.onMessage(message("payment.succeeded", "payment-settlement-service",
                "{\"bookingId\":\"%s\",\"paymentId\":\"nope\",\"status\":\"SUCCESS\",\"amount\":\"550\",\"currency\":\"LKR\"}"
                        .formatted(bookingId)));
        verify(bookings).confirmPaidFromEvent(bookingId, null, null, "LKR");
    }

    @Test
    void duplicateDeliveryIsHandledByTheInbox() {
        org.mockito.Mockito.doReturn(false).when(inbox).processOnce(any(), any(), any());
        listener.onMessage(payment("payment.failed", ",\"status\":\"FAILED\""));
        verify(bookings, never()).markPaymentFailed(any());
    }
}
