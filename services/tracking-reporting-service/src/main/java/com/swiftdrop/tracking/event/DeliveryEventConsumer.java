package com.swiftdrop.tracking.event;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.swiftdrop.tracking.config.RabbitMqConfig;

@Component
public class DeliveryEventConsumer {

    private final EventMessageHandler messageHandler;

    public DeliveryEventConsumer(EventMessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    @RabbitListener(queues = RabbitMqConfig.DELIVERY_QUEUE)
    public void consume(byte[] payload) {
        messageHandler.handle(payload, EventSource.DELIVERY_OPERATIONS);
    }
}
