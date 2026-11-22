package com.swiftdrop.payment.cod.service;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name="payment.cod.consumer.enabled", havingValue="true")
public class CustomerCodListener {
    private final CustomerCodService service;
    private final JsonMapper json;
    public CustomerCodListener(CustomerCodService service, JsonMapper json) { this.service=service; this.json=json; }

    @RabbitListener(queues="payment-settlement-service.customer-cod", ackMode="AUTO")
    public void receive(Message message) {
        CodCollectedEvent event;
        try {
            event = json.readValue(message.getBody(), CodCollectedEvent.class);
            if (event == null || event.eventId() == null
                    || !event.eventId().toString().equals(message.getMessageProperties().getMessageId()))
                throw new IllegalArgumentException("Mismatched message id");
        } catch (RuntimeException invalid) {
            throw new AmqpRejectAndDontRequeueException("Invalid customer COD envelope", invalid);
        }
        try { service.record(event); }
        catch (InvalidCodEvent invalid) {
            throw new AmqpRejectAndDontRequeueException("Invalid customer COD collection", invalid);
        }
        // Database/verifier outages are retried; acknowledge only after the receipt/outbox commit.
    }
}
