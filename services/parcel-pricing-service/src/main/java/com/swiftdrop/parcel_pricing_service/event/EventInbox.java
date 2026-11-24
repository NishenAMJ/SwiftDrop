package com.swiftdrop.parcel_pricing_service.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Runs an incoming event's effect at most once. The effect and the processed eventId are saved in
 * one transaction, as the payment contract asks of consumers, so a redelivery changes nothing.
 */
@Component
public class EventInbox {

    private final ProcessedEventRepository processed;

    public EventInbox(ProcessedEventRepository processed) {
        this.processed = processed;
    }

    /** @return false if this event was already processed */
    @Transactional
    public boolean processOnce(UUID eventId, String eventType, Runnable effect) {
        if (processed.existsById(eventId)) return false;
        effect.run();
        processed.save(new ProcessedEvent(eventId, eventType));
        return true;
    }
}
