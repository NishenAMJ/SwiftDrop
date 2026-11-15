package com.swiftdrop.communication.event;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EventEnvelope {

    private UUID eventId;
    private String eventType;
    private Integer version;
    private Instant occurredAt;
    private String producer;
    private JsonNode data;

    public EventEnvelope() {
    }

    public EventEnvelope(UUID eventId, String eventType, Integer version, Instant occurredAt, String producer, JsonNode data) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.version = version;
        this.occurredAt = occurredAt;
        this.producer = producer;
        this.data = data;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getProducer() {
        return producer;
    }

    public void setProducer(String producer) {
        this.producer = producer;
    }

    public JsonNode getData() {
        return data;
    }

    public void setData(JsonNode data) {
        this.data = data;
    }
}
