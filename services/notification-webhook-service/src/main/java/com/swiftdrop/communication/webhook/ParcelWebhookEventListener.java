package com.swiftdrop.communication.webhook;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.swiftdrop.communication.config.RabbitConfig;
import com.swiftdrop.communication.event.EnvelopeValidator;
import com.swiftdrop.communication.event.EventEnvelope;

@Component
public class ParcelWebhookEventListener {

    private final ParcelWebhookEventHandler handler;
    private final EnvelopeValidator envelopeValidator;

    public ParcelWebhookEventListener(ParcelWebhookEventHandler handler, EnvelopeValidator envelopeValidator) {
        this.handler = handler;
        this.envelopeValidator = envelopeValidator;
    }

    @RabbitListener(queues = RabbitConfig.PARCEL_WEBHOOKS_QUEUE)
    public void onMessage(Message message) {
        EventEnvelope envelope = envelopeValidator.readAndValidate(message);
        handler.handle(envelope);
    }
}
