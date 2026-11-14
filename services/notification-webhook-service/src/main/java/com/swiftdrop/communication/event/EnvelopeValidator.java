package com.swiftdrop.communication.event;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

@Component
public class EnvelopeValidator {

    private static final Logger log = LoggerFactory.getLogger(EnvelopeValidator.class);
    private final JsonMapper jsonMapper;

    public EnvelopeValidator(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public EventEnvelope readAndValidate(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        EventEnvelope envelope;
        try {
            envelope = jsonMapper.readValue(body, EventEnvelope.class);
        } catch (Exception e) {
            log.warn("Failed to parse event message as JSON, rejecting without requeue: {}", e.getClass().getSimpleName());
            throw new AmqpRejectAndDontRequeueException("Unreadable JSON payload", e);
        }

        if (envelope == null
                || envelope.getEventId() == null
                || envelope.getEventType() == null || envelope.getEventType().isBlank()
                || envelope.getOccurredAt() == null
                || envelope.getData() == null || envelope.getData().isNull() || envelope.getData().isMissingNode()) {
            log.warn("Event envelope missing required fields, rejecting without requeue");
            throw new AmqpRejectAndDontRequeueException("Missing required envelope fields");
        }

        return envelope;
    }
}
