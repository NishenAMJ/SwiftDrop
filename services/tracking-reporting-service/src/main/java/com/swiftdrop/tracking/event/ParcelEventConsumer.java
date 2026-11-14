package com.swiftdrop.tracking.event;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.swiftdrop.tracking.config.RabbitMqConfig;

@Component
public class ParcelEventConsumer {

    private final EventMessageHandler messageHandler;

    public ParcelEventConsumer(EventMessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    @RabbitListener(queues = RabbitMqConfig.PARCEL_QUEUE)
    public void consume(byte[] payload) {
        messageHandler.handle(payload, EventSource.PARCEL_PRICING);
    }
}
