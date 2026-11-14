package com.swiftdrop.tracking.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Component;

import com.swiftdrop.tracking.common.InvalidTrackingEventException;
import com.swiftdrop.tracking.tracking.TrackingProjectionUpdater;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class EventMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(EventMessageHandler.class);

    private final ObjectMapper objectMapper;
    private final TrackingEventValidator eventValidator;
    private final TrackingProjectionUpdater projectionUpdater;

    public EventMessageHandler(
            ObjectMapper objectMapper,
            TrackingEventValidator eventValidator,
            TrackingProjectionUpdater projectionUpdater) {
        this.objectMapper = objectMapper;
        this.eventValidator = eventValidator;
        this.projectionUpdater = projectionUpdater;
    }

    public void handle(byte[] payload, EventSource source) {
        try {
            EventEnvelope event = objectMapper.readValue(payload, EventEnvelope.class);
            eventValidator.validate(event, source);
            TrackingProjectionUpdater.Result result = projectionUpdater.process(event);
            if (result == TrackingProjectionUpdater.Result.DUPLICATE) {
                log.debug("Acknowledged duplicate tracking event {}", event.eventId());
            }
        } catch (JacksonException | InvalidTrackingEventException exception) {
            log.warn("Rejecting invalid tracking event from {}: {}", source, exception.getMessage());
            throw new AmqpRejectAndDontRequeueException("Invalid tracking event", exception);
        }
    }
}
