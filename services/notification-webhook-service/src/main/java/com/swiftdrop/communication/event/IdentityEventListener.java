package com.swiftdrop.communication.event;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.swiftdrop.communication.config.RabbitConfig;

@Component
public class IdentityEventListener {

    private final IdentityEventHandler handler;
    private final EnvelopeValidator envelopeValidator;

    public IdentityEventListener(IdentityEventHandler handler, EnvelopeValidator envelopeValidator) {
        this.handler = handler;
        this.envelopeValidator = envelopeValidator;
    }

    @RabbitListener(queues = RabbitConfig.IDENTITY_QUEUE)
    public void onMessage(Message message) {
        EventEnvelope envelope = envelopeValidator.readAndValidate(message);
        handler.handle(envelope);
    }
}
