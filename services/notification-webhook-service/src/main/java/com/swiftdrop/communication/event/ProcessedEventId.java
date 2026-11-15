package com.swiftdrop.communication.event;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ProcessedEventId implements Serializable {

    private UUID eventId;
    private String consumer;

    public ProcessedEventId() {
    }

    public ProcessedEventId(UUID eventId, String consumer) {
        this.eventId = eventId;
        this.consumer = consumer;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getConsumer() {
        return consumer;
    }

    public void setConsumer(String consumer) {
        this.consumer = consumer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProcessedEventId that = (ProcessedEventId) o;
        return Objects.equals(eventId, that.eventId) && Objects.equals(consumer, that.consumer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, consumer);
    }
}
