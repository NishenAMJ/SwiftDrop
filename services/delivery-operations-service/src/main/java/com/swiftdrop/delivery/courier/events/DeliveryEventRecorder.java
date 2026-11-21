package com.swiftdrop.delivery.courier.events;

import com.swiftdrop.delivery.courier.domain.AssignmentStatus;
import com.swiftdrop.delivery.courier.domain.DeliveryAssignment;
import com.swiftdrop.delivery.courier.domain.DeliveryAttempt;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Writes delivery events to the outbox in the same transaction as the change that caused them.
 * Shapes follow contracts/events/delivery-operations/delivery-status-events.schema.json.
 */
@Component
public class DeliveryEventRecorder {

    static final String PRODUCER = "delivery-operations-service";

    private final OutboxEventRepository outbox;
    private final JsonMapper json;

    public DeliveryEventRecorder(OutboxEventRepository outbox, JsonMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    /** The assignment moved to a non-failed status: assigned, picked up, in transit, out for delivery, delivered. */
    public void progressed(DeliveryAssignment assignment, Instant occurredAt) {
        var status = assignment.getStatus();
        var data = baseData(assignment, publicDescription(status));
        record(routingKey(status), occurredAt, data);
    }

    public void failed(DeliveryAssignment assignment, DeliveryAttempt attempt, Instant occurredAt) {
        var data = baseData(assignment, publicDescription(AssignmentStatus.DELIVERY_FAILED));
        data.put("attemptId", attempt.getId().toString());
        // Customer-safe category only; the courier's free-text notes never leave this service.
        data.put("failureCode", attempt.getReason().name());
        record(routingKey(AssignmentStatus.DELIVERY_FAILED), occurredAt, data);
    }

    static String routingKey(AssignmentStatus status) {
        return switch (status) {
            case ASSIGNED -> "delivery.assigned";
            case PICKED_UP -> "delivery.picked-up";
            case IN_TRANSIT -> "delivery.in-transit";
            case OUT_FOR_DELIVERY -> "delivery.out-for-delivery";
            case DELIVERED -> "delivery.delivered";
            case DELIVERY_FAILED -> "delivery.failed";
        };
    }

    static String publicDescription(AssignmentStatus status) {
        return switch (status) {
            case ASSIGNED -> "Assigned to a courier";
            case PICKED_UP -> "Picked up by the courier";
            case IN_TRANSIT -> "On its way";
            case OUT_FOR_DELIVERY -> "Out for delivery";
            case DELIVERED -> "Delivered";
            case DELIVERY_FAILED -> "Delivery attempt was unsuccessful";
        };
    }

    private LinkedHashMap<String, Object> baseData(DeliveryAssignment assignment, String description) {
        var data = new LinkedHashMap<String, Object>();
        data.put("parcelId", assignment.getParcelId().toString());
        data.put("trackingNumber", assignment.getParcelReference());
        data.put("deliveryStatus", assignment.getStatus().name());
        data.put("publicDescription", description);
        data.put("assignmentId", assignment.getId().toString());
        data.put("courierId", assignment.getCourier().getId().toString());
        return data;
    }

    private void record(String eventType, Instant occurredAt, LinkedHashMap<String, Object> data) {
        var eventId = UUID.randomUUID();
        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("version", 1);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("producer", PRODUCER);
        envelope.put("data", data);
        outbox.save(new OutboxEvent(eventId, eventType, json.writeValueAsString(envelope), Instant.now()));
    }
}
