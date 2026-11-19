package com.swiftdrop.payment.cod.service;

import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import org.slf4j.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "payment.cod.consumer.enabled", havingValue = "true")
public class CodCollectionListener {
    private static final Logger LOG = LoggerFactory.getLogger(CodCollectionListener.class);
    private final JsonMapper json;
    private final CodCollectionService service;

    public CodCollectionListener(JsonMapper json, CodCollectionService service) {
        this.json = json;
        this.service = service;
    }

    @RabbitListener(queues = "payment-settlement-service.cod", ackMode = "MANUAL")
    public void receive(Message message, Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag();
        CodCollectedEvent event;
        try {
            event = json.readValue(new String(message.getBody(), StandardCharsets.UTF_8), CodCollectedEvent.class);
        } catch (Exception malformed) {
            LOG.warn("Dead-lettering malformed COD event messageId={}",
                message.getMessageProperties().getMessageId());
            channel.basicReject(tag, false);
            return;
        }

        String messageId = message.getMessageProperties().getMessageId();
        if (event.eventId() == null || !event.eventId().toString().equals(messageId)) {
            LOG.warn("Dead-lettering COD event whose AMQP message_id does not match its eventId");
            channel.basicReject(tag, false);
            return;
        }

        try {
            service.record(event);
            channel.basicAck(tag, false);
        } catch (InvalidCodEvent invalid) {
            LOG.warn("Dead-lettering invalid COD event eventId={} reason={}", event.eventId(), invalid.getMessage());
            channel.basicReject(tag, false);
        } catch (DataIntegrityViolationException conflict) {
            LOG.warn("Dead-lettering conflicting COD event eventId={}", event.eventId());
            channel.basicReject(tag, false);
        } catch (RuntimeException transientFailure) {
            LOG.warn("Requeueing COD event after processing failure eventId={} type={}",
                event.eventId(), transientFailure.getClass().getSimpleName());
            channel.basicNack(tag, false, true);
        }
    }
}
