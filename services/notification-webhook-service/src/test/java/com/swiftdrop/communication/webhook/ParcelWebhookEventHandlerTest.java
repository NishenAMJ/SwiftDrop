package com.swiftdrop.communication.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.communication.event.EnvelopeValidator;
import com.swiftdrop.communication.event.EventEnvelope;
import com.swiftdrop.communication.event.ProcessedEvent;
import com.swiftdrop.communication.event.ProcessedEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import tools.jackson.databind.json.JsonMapper;

/** Fan-out of parcel events to merchant webhook subscriptions, plus envelope validation. */
class ParcelWebhookEventHandlerTest {
    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");
    private final JsonMapper json = JsonMapper.builder().build();
    private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    private final WebhookSubscriptionRepository subscriptions = mock(WebhookSubscriptionRepository.class);
    private final WebhookDeliveryRepository deliveries = mock(WebhookDeliveryRepository.class);
    private final ParcelWebhookEventHandler handler = new ParcelWebhookEventHandler(processed, subscriptions, deliveries, json,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID merchantId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    private EventEnvelope envelope(String type, UUID merchant) {
        String data = """
            {"parcelId":"%s","trackingNumber":"SWD-2026-000123","status":"OUT_FOR_DELIVERY","previousStatus":"IN_TRANSIT",
             "customerUserId":"%s","recipientPhone":"+94771234567"%s}
            """.formatted(UUID.randomUUID(), UUID.randomUUID(), merchant == null ? "" : ",\"merchantId\":\"" + merchant + "\"");
        return new EventEnvelope(eventId, type, 1, NOW, "parcel-pricing-service", json.readTree(data));
    }

    private WebhookSubscription subscription(String eventTypes) {
        return new WebhookSubscription(UUID.randomUUID(), merchantId, "https://merchant.example/hook", eventTypes, "c",
                true, null, NOW, NOW);
    }

    @Test
    void duplicateEventIsIgnored() {
        when(processed.existsByEventIdAndConsumer(eventId, ParcelWebhookEventHandler.CONSUMER)).thenReturn(true);
        handler.handle(envelope("parcel.status-changed", merchantId));
        verifyNoInteractions(subscriptions, deliveries);
        verify(processed, never()).save(any());
    }

    @Test
    void customerBookingsWithoutAMerchantSendNoWebhooksButAreMarkedProcessed() {
        handler.handle(envelope("parcel.status-changed", null));
        verifyNoInteractions(subscriptions, deliveries);
        verify(processed).save(any(ProcessedEvent.class));
    }

    @Test
    void onlyMatchingAndWildcardSubscriptionsReceiveADelivery() {
        var exact = subscription("parcel.booked, parcel.status-changed");
        var wildcard = subscription("*");
        var other = subscription("parcel.cancelled");
        when(subscriptions.findByMerchantIdAndActiveTrue(merchantId)).thenReturn(List.of(exact, wildcard, other));

        handler.handle(envelope("PARCEL.STATUS-CHANGED", merchantId));

        ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveries, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(WebhookDelivery::getSubscriptionId)
                .containsExactlyInAnyOrder(exact.getSubscriptionId(), wildcard.getSubscriptionId());
        assertThat(captor.getAllValues()).allSatisfy(d -> {
            assertThat(d.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
            assertThat(d.getNextAttemptAt()).isEqualTo(NOW);
        });
    }

    @Test
    void merchantPayloadNeverContainsCustomerPersonalData() throws Exception {
        when(subscriptions.findByMerchantIdAndActiveTrue(merchantId)).thenReturn(List.of(subscription("*")));
        handler.handle(envelope("parcel.status-changed", merchantId));
        ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveries).save(captor.capture());
        String payload = captor.getValue().getPayload();
        assertThat(payload).contains("SWD-2026-000123").contains("OUT_FOR_DELIVERY")
                .doesNotContain("customerUserId").doesNotContain("+94771234567").doesNotContain("recipientPhone");
    }

    @Test
    void existingDeliveryForTheSameEventIsNotDuplicated() {
        var subscription = subscription("*");
        when(subscriptions.findByMerchantIdAndActiveTrue(merchantId)).thenReturn(List.of(subscription));
        when(deliveries.existsBySubscriptionIdAndEventId(subscription.getSubscriptionId(), eventId)).thenReturn(true);
        handler.handle(envelope("parcel.status-changed", merchantId));
        verify(deliveries, never()).save(any());
        verify(processed).save(any(ProcessedEvent.class));
    }

    // ---------- envelope validation ----------

    private final EnvelopeValidator validator = new EnvelopeValidator(json);

    private static Message message(String body) {
        return new Message(body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void validEnvelopeIsAccepted() {
        var envelope = validator.readAndValidate(message("""
            {"eventId":"%s","eventType":"parcel.booked","version":1,"occurredAt":"2026-11-22T10:00:00Z","data":{"a":1}}
            """.formatted(eventId)));
        assertThat(envelope.getEventId()).isEqualTo(eventId);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not json",
            "{\"eventType\":\"parcel.booked\",\"occurredAt\":\"2026-11-22T10:00:00Z\",\"data\":{}}",
            "{\"eventId\":\"6c0d1c1e-3a52-4c8e-9f0c-1f2a3b4c5d6e\",\"eventType\":\" \",\"occurredAt\":\"2026-11-22T10:00:00Z\",\"data\":{}}",
            "{\"eventId\":\"6c0d1c1e-3a52-4c8e-9f0c-1f2a3b4c5d6e\",\"eventType\":\"parcel.booked\",\"data\":{}}",
            "{\"eventId\":\"6c0d1c1e-3a52-4c8e-9f0c-1f2a3b4c5d6e\",\"eventType\":\"parcel.booked\",\"occurredAt\":\"2026-11-22T10:00:00Z\"}",
            "{\"eventId\":\"6c0d1c1e-3a52-4c8e-9f0c-1f2a3b4c5d6e\",\"eventType\":\"parcel.booked\",\"occurredAt\":\"2026-11-22T10:00:00Z\",\"data\":null}",
            "null"})
    void incompleteEnvelopesAreDeadLettered(String body) {
        assertThatThrownBy(() -> validator.readAndValidate(message(body))).isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
