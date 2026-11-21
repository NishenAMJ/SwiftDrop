package com.swiftdrop.delivery.courier.events;

import com.swiftdrop.delivery.courier.service.DeliveryAssignmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Stops an open assignment when Parcel & Pricing cancels the booking (parcel.cancelled, see
 * contracts/events/parcel-pricing/parcel-cancelled.schema.json). A booking can only be cancelled before a
 * courier is scheduled, so this is the rare case where the cancellation and an assignment cross.
 */
@Component
public class ParcelCancelledListener {

    private static final Logger LOG = LoggerFactory.getLogger(ParcelCancelledListener.class);

    private final DeliveryAssignmentService assignments;
    private final JsonMapper json;

    public ParcelCancelledListener(DeliveryAssignmentService assignments, JsonMapper json) {
        this.assignments = assignments;
        this.json = json;
    }

    @RabbitListener(queues = DeliveryEventsConfig.PARCEL_CANCELLED_QUEUE)
    public void receive(Message message) {
        UUID parcelId;
        try {
            var event = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            if (!"parcel.cancelled".equals(event.path("eventType").asString())) {
                throw new IllegalArgumentException("not a parcel.cancelled event");
            }
            parcelId = UUID.fromString(event.path("data").path("parcelId").asString());
        } catch (Exception malformed) {
            // A message we can never understand goes to the dead-letter queue, not round and round.
            throw new AmqpRejectAndDontRequeueException("Unreadable parcel.cancelled event", malformed);
        }
        var stopped = assignments.cancelForParcel(parcelId);
        if (stopped > 0) {
            LOG.info("Stopped {} open assignment(s) for cancelled parcel {}", stopped, parcelId);
        }
    }
}
