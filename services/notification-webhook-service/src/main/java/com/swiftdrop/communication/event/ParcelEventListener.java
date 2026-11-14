package com.swiftdrop.communication.event;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.swiftdrop.communication.config.RabbitConfig;

@Component
public class ParcelEventListener {

    private final ParcelNotificationHandler handler;
    private final EnvelopeValidator envelopeValidator;

    public ParcelEventListener(ParcelNotificationHandler handler, EnvelopeValidator envelopeValidator) {
        this.handler = handler;
        this.envelopeValidator = envelopeValidator;
    }

    @RabbitListener(queues = RabbitConfig.PARCEL_QUEUE)
    public void onMessage(Message message) {
        EventEnvelope envelope = envelopeValidator.readAndValidate(message);
        handler.handle(envelope);
    }
}
