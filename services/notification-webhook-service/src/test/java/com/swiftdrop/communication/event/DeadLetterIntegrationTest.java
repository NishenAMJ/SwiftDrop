package com.swiftdrop.communication.event;

import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import com.swiftdrop.communication.config.RabbitConfig;
import com.swiftdrop.communication.support.AbstractIntegrationTest;

/**
 * A message the service cannot read must not be lost or retried forever: it goes through the
 * real broker into notification-webhook-service.dead-letter.
 */
class DeadLetterIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void unreadableIdentityEventEndsUpInDeadLetterQueue() {
        assertDeadLettered(RabbitConfig.IDENTITY_EXCHANGE, "user.registered");
    }

    @Test
    void unreadableParcelEventEndsUpInDeadLetterQueue() {
        assertDeadLettered(RabbitConfig.DELIVERY_EXCHANGE, RabbitConfig.ROUTING_KEY_PARCEL_STATUS_CHANGED);
    }

    private void assertDeadLettered(String exchange, String routingKey) {
        String marker = "not-json-" + UUID.randomUUID();
        rabbitTemplate.convertAndSend(exchange, routingKey, marker);

        AtomicBoolean found = new AtomicBoolean();
        await().atMost(Duration.ofSeconds(20)).until(() -> {
            Message message;
            while ((message = rabbitTemplate.receive(RabbitConfig.DEAD_LETTER_QUEUE, 500)) != null) {
                if (marker.equals(new String(message.getBody(), StandardCharsets.UTF_8))) {
                    found.set(true);
                }
            }
            return found.get();
        });
    }
}
