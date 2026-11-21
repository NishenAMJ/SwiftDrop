package com.swiftdrop.delivery.courier.events;

import com.swiftdrop.delivery.courier.domain.AssignmentStatus;
import com.swiftdrop.delivery.courier.domain.DeliveryAssignment;
import com.swiftdrop.delivery.courier.domain.DeliveryAttempt;
import com.swiftdrop.delivery.courier.domain.FailureReason;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;

/**
 * Writes delivery events to the outbox in the same transaction as the change that caused them.
 *
 * <ul>
 *   <li>{@code delivery.*} for Tracking, to swiftdrop.delivery-operations, following
 *       contracts/events/delivery-operations/delivery-status-events.schema.json</li>
 *   <li>{@code parcel.status-changed} for notification-webhook-service, to swiftdrop.delivery,
 *       following contracts/events/parcel/parcel-status-changed.schema.json. That contract has its
 *       own status names, so this service translates to them; nothing there is renamed.</li>
 * </ul>
 */
@Component
public class DeliveryEventRecorder {

    static final String PRODUCER = "delivery-operations-service";
    static final String PARCEL_STATUS_CHANGED = "parcel.status-changed";

    private final OutboxEventRepository outbox;
    private final JsonMapper json;

    public DeliveryEventRecorder(OutboxEventRepository outbox, JsonMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    /**
     * The assignment moved to a non-failed status: assigned, picked up, in transit, out for delivery
     * or delivered.
     *
     * @param previous the status it moved from; null when the parcel has just been assigned
     */
    public void progressed(DeliveryAssignment assignment, AssignmentStatus previous, Instant occurredAt) {
        var status = assignment.getStatus();
        var data = baseData(assignment, publicDescription(status));
        record(DeliveryEventsConfig.DELIVERY_OPERATIONS_EXCHANGE, routingKey(status), occurredAt, data);
        recordCustomerStatus(assignment, previous, null, occurredAt);
    }

    public void failed(DeliveryAssignment assignment, DeliveryAttempt attempt, Instant occurredAt) {
        var data = baseData(assignment, publicDescription(AssignmentStatus.DELIVERY_FAILED));
        data.put("attemptId", attempt.getId().toString());
        // Customer-safe category only; the courier's free-text notes never leave this service.
        data.put("failureCode", attempt.getReason().name());
        record(DeliveryEventsConfig.DELIVERY_OPERATIONS_EXCHANGE, routingKey(AssignmentStatus.DELIVERY_FAILED),
                occurredAt, data);
        recordCustomerStatus(assignment, AssignmentStatus.OUT_FOR_DELIVERY, attempt.getReason(), occurredAt);
    }

    /** parcel.status-changed, for steps the customer hears about. Assigning a courier is not one of them. */
    private void recordCustomerStatus(DeliveryAssignment assignment, AssignmentStatus previous,
                                      FailureReason failure, Instant occurredAt) {
        var status = customerStatus(assignment.getStatus());
        if (status.isEmpty()) {
            return;
        }
        var data = new LinkedHashMap<String, Object>();
        data.put("parcelId", assignment.getParcelId().toString());
        data.put("trackingNumber", assignment.getParcelReference());
        data.put("status", status.get());
        if (previous != null) {
            customerStatus(previous).ifPresent(value -> data.put("previousStatus", value));
        }
        // Without a customer there is nobody to notify; the contract leaves the field out then.
        if (assignment.getCustomerId() != null) {
            data.put("customerUserId", assignment.getCustomerId().toString());
        }
        if (failure != null) {
            data.put("failureReason", customerFailureReason(failure));
        }
        data.put("changedAt", occurredAt.toString());
        record(DeliveryEventsConfig.NOTIFICATION_EXCHANGE, PARCEL_STATUS_CHANGED, occurredAt, data);
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

    /**
     * The status names of parcel-status-changed.schema.json: CREATED, PICKED_UP, IN_TRANSIT,
     * OUT_FOR_DELIVERY, DELIVERED, FAILED. ASSIGNED has no equivalent there.
     */
    static Optional<String> customerStatus(AssignmentStatus status) {
        return switch (status) {
            case ASSIGNED -> Optional.empty();
            case PICKED_UP -> Optional.of("PICKED_UP");
            case IN_TRANSIT -> Optional.of("IN_TRANSIT");
            case OUT_FOR_DELIVERY -> Optional.of("OUT_FOR_DELIVERY");
            case DELIVERED -> Optional.of("DELIVERED");
            case DELIVERY_FAILED -> Optional.of("FAILED");
        };
    }

    /** Wording that is safe to show a customer: no addresses, no courier notes. */
    static String customerFailureReason(FailureReason reason) {
        return switch (reason) {
            case RECIPIENT_UNAVAILABLE -> "The recipient was not available";
            case INCORRECT_ADDRESS -> "The delivery address could not be found";
            case RECIPIENT_REFUSED -> "The parcel was not accepted";
            case OTHER -> "The delivery could not be completed";
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

    private void record(String exchange, String eventType, Instant occurredAt, LinkedHashMap<String, Object> data) {
        var eventId = UUID.randomUUID();
        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("version", 1);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("producer", PRODUCER);
        envelope.put("data", data);
        outbox.save(new OutboxEvent(eventId, exchange, eventType, json.writeValueAsString(envelope), Instant.now()));
    }
}
