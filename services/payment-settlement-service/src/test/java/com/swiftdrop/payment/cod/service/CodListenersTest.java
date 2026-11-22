package com.swiftdrop.payment.cod.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

/** Ack / dead-letter / requeue decisions of the COD queue listeners. */
class CodListenersTest {
    private static final long TAG = 42L;
    private final JsonMapper json = JsonMapper.builder().build();

    private static Message message(String body, String messageId) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(TAG);
        properties.setMessageId(messageId);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }

    private static String event(UUID eventId) {
        return """
            {"eventId":"%s","eventType":"cod.collected","version":1,"occurredAt":"2026-11-22T00:00:00Z",
             "producer":"delivery-operations-service","data":{"bookingId":"B-1","trackingNumber":"SWD-2026-000123",
             "pricingSnapshotId":"SNP-1","merchantId":"846c790e-497a-4340-8039-79e25c95f40e",
             "courierId":"aa6c790e-497a-4340-8039-79e25c95f40e","deliveryAttemptId":"bb6c790e-497a-4340-8039-79e25c95f40e",
             "paymentMethod":"COD","expectedAmount":1000.00,"collectedAmount":1000.00,"currency":"LKR",
             "collectedAt":"2026-11-22T00:00:00Z"}}
            """.formatted(eventId);
    }

    // ---------- merchant COD (manual ack) ----------

    private final CodCollectionService codService = mock(CodCollectionService.class);
    private final Channel channel = mock(Channel.class);
    private final CodCollectionListener listener = new CodCollectionListener(json, codService);

    @Test
    void validEventIsRecordedAndAcknowledged() throws Exception {
        UUID id = UUID.randomUUID();
        listener.receive(message(event(id), id.toString()), channel);
        verify(codService).record(any());
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void malformedJsonIsDeadLetteredWithoutProcessing() throws Exception {
        listener.receive(message("{not json", "m-1"), channel);
        verify(channel).basicReject(TAG, false);
        verifyNoInteractions(codService);
    }

    @Test
    void messageIdThatDoesNotMatchTheEventIdIsDeadLettered() throws Exception {
        listener.receive(message(event(UUID.randomUUID()), UUID.randomUUID().toString()), channel);
        listener.receive(message(event(UUID.randomUUID()), null), channel);
        verify(channel, org.mockito.Mockito.times(2)).basicReject(TAG, false);
        verifyNoInteractions(codService);
    }

    @Test
    void invalidAndConflictingEventsAreDeadLetteredNotRequeued() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new InvalidCodEvent("bad amount")).when(codService).record(any());
        listener.receive(message(event(id), id.toString()), channel);

        doThrow(new DataIntegrityViolationException("duplicate")).when(codService).record(any());
        listener.receive(message(event(id), id.toString()), channel);

        verify(channel, org.mockito.Mockito.times(2)).basicReject(TAG, false);
        verify(channel, never()).basicNack(TAG, false, true);
        verify(channel, never()).basicAck(TAG, false);
    }

    @Test
    void transientFailuresAreRequeued() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new CodVerificationUnavailable("parcel service down")).when(codService).record(any());
        listener.receive(message(event(id), id.toString()), channel);
        verify(channel).basicNack(TAG, false, true);
        verify(channel, never()).basicAck(TAG, false);
    }

    // ---------- customer COD (auto ack) ----------

    private final CustomerCodService customerService = mock(CustomerCodService.class);
    private final CustomerCodListener customerListener = new CustomerCodListener(customerService, json);

    @Test
    void customerEventIsRecorded() {
        UUID id = UUID.randomUUID();
        assertThatCode(() -> customerListener.receive(message(event(id), id.toString()))).doesNotThrowAnyException();
        verify(customerService).record(any());
    }

    @Test
    void customerEnvelopeProblemsAreRejectedWithoutRequeue() {
        assertThatThrownBy(() -> customerListener.receive(message("[]", "m-1")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> customerListener.receive(message(event(UUID.randomUUID()), "other-id")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(customerService);
    }

    @Test
    void invalidCustomerCollectionIsRejectedButOutagesPropagateForRetry() {
        UUID id = UUID.randomUUID();
        doThrow(new InvalidCodEvent("short payment")).when(customerService).record(any());
        assertThatThrownBy(() -> customerListener.receive(message(event(id), id.toString())))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        doThrow(new CodVerificationUnavailable("offline")).when(customerService).record(any());
        assertThatThrownBy(() -> customerListener.receive(message(event(id), id.toString())))
                .isInstanceOf(CodVerificationUnavailable.class);
    }
}
