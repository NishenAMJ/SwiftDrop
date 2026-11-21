package com.swiftdrop.parcel_pricing_service.event;

import com.swiftdrop.parcel_pricing_service.service.BookingService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Payment & Settlement events. payment.succeeded confirms an online booking and issues its
 * receipt even when the customer never came back to the website after paying.
 */
@Component
@ConditionalOnProperty(name = "swiftdrop.events.listeners.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentEventListener {

    private static final String PAYMENT_PRODUCER = "payment-settlement-service";

    private final JsonMapper json;
    private final EventInbox inbox;
    private final BookingService bookingService;

    public PaymentEventListener(JsonMapper json, EventInbox inbox, BookingService bookingService) {
        this.json = json;
        this.inbox = inbox;
        this.bookingService = bookingService;
    }

    @RabbitListener(queues = EventNames.PAYMENT_QUEUE)
    public void onMessage(Message message) {
        IncomingEvent event = IncomingEvent.parse(message.getBody(), json);
        if (!PAYMENT_PRODUCER.equals(event.producer())) {
            throw new AmqpRejectAndDontRequeueException("Unexpected producer for " + event.eventType());
        }
        String bookingId = event.text("bookingId");
        String status = event.text("status");
        if (bookingId == null) throw new AmqpRejectAndDontRequeueException("Payment event without bookingId");

        Runnable effect = switch (event.eventType()) {
            case "payment.succeeded" -> () -> {
                if ("SUCCESS".equals(status)) {
                    bookingService.confirmPaidFromEvent(bookingId, event.uuid("paymentId"),
                            event.decimal("amount"), event.text("currency"));
                }
            };
            case "payment.failed" -> () -> bookingService.markPaymentFailed(bookingId);
            case "payment.refunded" -> () -> bookingService.markRefunded(bookingId, status);
            default -> null;
        };
        if (effect != null) inbox.processOnce(event.eventId(), event.eventType(), effect);
    }
}
