package com.swiftdrop.identity.event;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * Records an event in the outbox, wrapped in the shared envelope from
 * contracts/events/envelope.schema.json. Must be called inside the transaction that makes the
 * change, so the change and its event are saved together or not at all.
 */
@Component
public class EventRecorder {

    static final String PRODUCER = "identity-merchant-service";

    private final OutboxEventRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public EventRecorder(OutboxEventRepository repository, JsonMapper jsonMapper, Clock clock) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String eventType, Map<String, Object> data) {
        UUID eventId = UUID.randomUUID();
        Instant now = clock.instant();

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("version", 1);
        envelope.put("occurredAt", now.toString());
        envelope.put("producer", PRODUCER);
        envelope.put("data", data);

        repository.save(new OutboxEvent(eventId, eventType, jsonMapper.writeValueAsString(envelope), now));
    }
}
