package com.swiftdrop.payment.onlinepayment.service;

import java.nio.charset.StandardCharsets;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="payment.cancellation.consumer.enabled",havingValue="true")
public class CancellationListener {
    private final CancellationService service;
    public CancellationListener(CancellationService service) { this.service=service; }
    @RabbitListener(queues="payment-settlement-service.cancellations",ackMode="AUTO")
    public void receive(Message message) {
        try { service.accept(new String(message.getBody(),StandardCharsets.UTF_8)); }
        catch(IllegalArgumentException | tools.jackson.core.JacksonException invalid) {
            throw new AmqpRejectAndDontRequeueException("Invalid or conflicting cancellation event",invalid);
        }
        // AUTO acknowledgement follows accept's committed transaction. Database failures are retried.
    }
}
